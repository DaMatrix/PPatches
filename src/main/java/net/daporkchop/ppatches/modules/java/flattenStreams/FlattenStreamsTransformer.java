package net.daporkchop.ppatches.modules.java.flattenStreams;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.daporkchop.ppatches.util.TypeUtils;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.InvokeDynamicUtils;
import net.daporkchop.ppatches.util.asm.LVTReference;
import org.apache.commons.lang3.mutable.MutableInt;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.*;
import java.util.function.*;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class FlattenStreamsTransformer implements ITreeClassTransformer {
    private static boolean isStreamType(String internalName) {
        switch (internalName) {
            case "java/util/stream/Stream":
            case "java/util/stream/IntStream":
            case "java/util/stream/LongStream":
            case "java/util/stream/DoubleStream":
                return true;
        }
        return false;
    }

    private static boolean isStreamType(Type type) {
        return BytecodeHelper.isReference(type) && isStreamType(type.getInternalName());
    }

    private static Type getRawElementType(String streamTypeInternalName) {
        switch (streamTypeInternalName) {
            case "java/util/stream/Stream":
                return Type.getType(Object.class);
            case "java/util/stream/IntStream":
                return Type.INT_TYPE;
            case "java/util/stream/LongStream":
                return Type.LONG_TYPE;
            case "java/util/stream/DoubleStream":
                return Type.DOUBLE_TYPE;
        }
        throw new IllegalArgumentException("not a stream class: " + streamTypeInternalName);
    }

    private static Type getRawElementType(Type streamType) {
        return getRawElementType(streamType.getInternalName());
    }

    private static String getStreamTypeInternalNameFromElementType(Type elementType) {
        switch (elementType.getSort()) {
            case Type.ARRAY:
            case Type.OBJECT:
                return "java/util/stream/Stream";
            case Type.INT:
                return "java/util/stream/IntStream";
            case Type.LONG:
                return "java/util/stream/LongStream";
            case Type.DOUBLE:
                return "java/util/stream/DoubleStream";
        }
        throw new IllegalArgumentException("not a stream element type: " + elementType);
    }

    private static Type getStreamTypeFromElementType(Type elementType) {
        return Type.getObjectType(getStreamTypeInternalNameFromElementType(elementType));
    }

    private static final Type[] ELEMENT_TYPES = {
            Type.getType(Object.class),
            Type.INT_TYPE,
            Type.LONG_TYPE,
            Type.DOUBLE_TYPE,
    };

    private static final Map<Type, PerElementTypeInfo> ELEMENT_TYPE_INFO = ImmutableMap.<Type, PerElementTypeInfo>builder()
            .put(Type.getType(Object.class), new PerElementTypeInfo(
                    Type.getType(Stream.class), Type.getType(Object.class),
                    Type.getType(Object[].class), Type.getType(ObjectArrayList.class), Type.getType(Optional.class), null,
                    Type.getType(Predicate.class),
                    Type.getType(Consumer.class),
                    new Type[]{Type.getType(Comparator.class)},
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultMapTypeInfo>builder()
                            .put(Type.getType(Object.class), new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.getType(Object.class), "map", Type.getType(Function.class), "apply"))
                            .put(Type.INT_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.INT_TYPE, "mapToInt", Type.getType(ToIntFunction.class), "applyAsInt"))
                            .put(Type.LONG_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.LONG_TYPE, "mapToLong", Type.getType(ToLongFunction.class), "applyAsLong"))
                            .put(Type.DOUBLE_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.DOUBLE_TYPE, "mapToDouble", Type.getType(ToDoubleFunction.class), "applyAsDouble"))
                            .build(),
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultMapTypeInfo>builder()
                            .put(Type.getType(Object.class), new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.getType(Object.class), "flatMap", Type.getType(Function.class), "apply"))
                            .put(Type.INT_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.INT_TYPE, "flatMapToInt", Type.getType(Function.class), "apply"))
                            .put(Type.LONG_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.LONG_TYPE, "flatMapToLong", Type.getType(Function.class), "apply"))
                            .put(Type.DOUBLE_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.DOUBLE_TYPE, "flatMapToDouble", Type.getType(Function.class), "apply"))
                            .build(),
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultConvertTypeInfo>builder()
                            .build()))

            .put(Type.INT_TYPE, new PerElementTypeInfo(
                    Type.getType(IntStream.class), Type.INT_TYPE,
                    Type.getType(int[].class), Type.getType(IntArrayList.class), Type.getType(OptionalInt.class), Type.getType(IntSummaryStatistics.class),
                    Type.getType(IntPredicate.class),
                    Type.getType(IntConsumer.class),
                    new Type[0],
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultMapTypeInfo>builder()
                            .put(Type.getType(Object.class), new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.getType(Object.class), "mapToObj", Type.getType(IntFunction.class), "apply"))
                            .put(Type.INT_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.INT_TYPE, "map", Type.getType(IntUnaryOperator.class), "applyAsInt"))
                            .put(Type.LONG_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.LONG_TYPE, "mapToLong", Type.getType(IntToLongFunction.class), "applyAsLong"))
                            .put(Type.DOUBLE_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.DOUBLE_TYPE, "mapToDouble", Type.getType(IntToDoubleFunction.class), "applyAsDouble"))
                            .build(),
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultMapTypeInfo>builder()
                            .put(Type.INT_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.INT_TYPE, "flatMap", Type.getType(IntFunction.class), "apply"))
                            .build(),
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultConvertTypeInfo>builder()
                            .put(Type.getType(Object.class), new PerElementTypeInfo.PerResultConvertTypeInfo(
                                    Type.getType(Object.class), "boxed", BytecodeHelper.generateBoxingConversion(Type.INT_TYPE)))
                            .put(Type.LONG_TYPE, new PerElementTypeInfo.PerResultConvertTypeInfo(
                                    Type.LONG_TYPE, "asLongStream", new InsnNode(I2L)))
                            .put(Type.DOUBLE_TYPE, new PerElementTypeInfo.PerResultConvertTypeInfo(
                                    Type.DOUBLE_TYPE, "asDoubleStream", new InsnNode(I2D)))
                            .build()))

            .put(Type.LONG_TYPE, new PerElementTypeInfo(
                    Type.getType(LongStream.class), Type.LONG_TYPE,
                    Type.getType(long[].class), Type.getType(LongArrayList.class), Type.getType(OptionalLong.class), Type.getType(LongSummaryStatistics.class),
                    Type.getType(LongPredicate.class),
                    Type.getType(LongConsumer.class),
                    new Type[0],
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultMapTypeInfo>builder()
                            .put(Type.getType(Object.class), new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.getType(Object.class), "mapToObj", Type.getType(LongFunction.class), "apply"))
                            .put(Type.INT_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.INT_TYPE, "mapToInt", Type.getType(LongToIntFunction.class), "applyAsInt"))
                            .put(Type.LONG_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.LONG_TYPE, "map", Type.getType(LongUnaryOperator.class), "applyAsLong"))
                            .put(Type.DOUBLE_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.DOUBLE_TYPE, "mapToDouble", Type.getType(LongToDoubleFunction.class), "applyAsDouble"))
                            .build(),
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultMapTypeInfo>builder()
                            .put(Type.LONG_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.LONG_TYPE, "flatMap", Type.getType(LongFunction.class), "apply"))
                            .build(),
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultConvertTypeInfo>builder()
                            .put(Type.getType(Object.class), new PerElementTypeInfo.PerResultConvertTypeInfo(
                                    Type.getType(Object.class), "boxed", BytecodeHelper.generateBoxingConversion(Type.LONG_TYPE)))
                            .put(Type.DOUBLE_TYPE, new PerElementTypeInfo.PerResultConvertTypeInfo(
                                    Type.DOUBLE_TYPE, "asDoubleStream", new InsnNode(L2D)))
                            .build()))

            .put(Type.DOUBLE_TYPE, new PerElementTypeInfo(
                    Type.getType(DoubleStream.class), Type.DOUBLE_TYPE,
                    Type.getType(double[].class), Type.getType(DoubleArrayList.class), Type.getType(OptionalDouble.class), Type.getType(DoubleSummaryStatistics.class),
                    Type.getType(DoublePredicate.class),
                    Type.getType(DoubleConsumer.class),
                    new Type[0],
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultMapTypeInfo>builder()
                            .put(Type.getType(Object.class), new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.getType(Object.class), "mapToObj", Type.getType(DoubleFunction.class), "apply"))
                            .put(Type.INT_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.INT_TYPE, "mapToInt", Type.getType(DoubleToIntFunction.class), "applyAsInt"))
                            .put(Type.LONG_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.LONG_TYPE, "mapToLong", Type.getType(DoubleToLongFunction.class), "applyAsLong"))
                            .put(Type.DOUBLE_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.DOUBLE_TYPE, "mapToDouble", Type.getType(DoubleUnaryOperator.class), "applyAsDouble"))
                            .build(),
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultMapTypeInfo>builder()
                            .put(Type.DOUBLE_TYPE, new PerElementTypeInfo.PerResultMapTypeInfo(
                                    Type.DOUBLE_TYPE, "flatMap", Type.getType(DoubleFunction.class), "apply"))
                            .build(),
                    ImmutableMap.<Type, PerElementTypeInfo.PerResultConvertTypeInfo>builder()
                            .put(Type.getType(Object.class), new PerElementTypeInfo.PerResultConvertTypeInfo(
                                    Type.getType(Object.class), "boxed", BytecodeHelper.generateBoxingConversion(Type.DOUBLE_TYPE)))
                            .build()))
            .build();

    @RequiredArgsConstructor
    private static final class PerElementTypeInfo {
        public final Type streamType;
        public final Type elementType;

        public final Type elementArrayType;
        public final Type elementArrayListType;
        public final Type elementOptionalType;
        public final Type elementSummaryStatisticsType;

        public final Type elementPredicateType;
        public final Type elementConsumerType;

        public final Type[] comparatorArray;

        public final Map<Type, PerResultMapTypeInfo> mapInfo;
        public final Map<Type, PerResultMapTypeInfo> flatMapInfo;

        @RequiredArgsConstructor
        private static final class PerResultMapTypeInfo {
            public final Type mappedElementType;

            public final String mapMethodName;
            public final Type mapFunctionType;

            public final String mapFunctionApplyMethodName;
        }

        public final Map<Type, PerResultConvertTypeInfo> convertInfo;

        @RequiredArgsConstructor
        private static final class PerResultConvertTypeInfo {
            public final Type convertedElementType;

            public final String convertMethodName;
            public final AbstractInsnNode convertInsn;
        }
    }

    private static Optional<? extends Source> tryMapSourceStage(MethodNode methodNode, MethodInsnNode insn) {
        Type streamClass = Type.getReturnType(insn.desc);
        if (isStreamType(streamClass)) {
            String streamClassInternalName = streamClass.getInternalName();
            Type elementType = getRawElementType(streamClass);
            PerElementTypeInfo info = ELEMENT_TYPE_INFO.get(elementType);

            if (BytecodeHelper.isINVOKESTATIC(insn, streamClassInternalName, "empty", Type.getMethodDescriptor(streamClass))) {
                return Optional.of(new Source.OfEmpty(insn));
            } else if (BytecodeHelper.isINVOKESTATIC(insn, streamClassInternalName, "of", Type.getMethodDescriptor(streamClass, elementType))) {
                return Optional.of(new Source.OfSingle(insn));
            } else if (BytecodeHelper.isINVOKESTATIC(insn, streamClassInternalName, "of", Type.getMethodDescriptor(streamClass, info.elementArrayType))
                       || BytecodeHelper.isINVOKESTATIC(insn, Type.getInternalName(Arrays.class), "stream", Type.getMethodDescriptor(streamClass, info.elementArrayType))) {
                return Optional.of(new Source.OfArray(insn, 0));
            } else if (BytecodeHelper.isINVOKESTATIC(insn, Type.getInternalName(Arrays.class), "stream", Type.getMethodDescriptor(streamClass, info.elementArrayType, Type.INT_TYPE, Type.INT_TYPE))) {
                return Optional.of(new Source.OfArraySubsequence(insn, 0));
            } else if (BytecodeHelper.isINVOKESTATIC(insn, streamClassInternalName, "range", Type.getMethodDescriptor(streamClass, elementType, elementType))) {
                return Optional.of(new Source.OfRange(insn, false));
            } else if (BytecodeHelper.isINVOKESTATIC(insn, streamClassInternalName, "rangeClosed", Type.getMethodDescriptor(streamClass, elementType, elementType))) {
                return Optional.of(new Source.OfRange(insn, true));
            } else if ((BytecodeHelper.isINVOKEVIRTUAL(insn, insn.owner, "stream", Type.getMethodDescriptor(streamClass))
                        || BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "stream", Type.getMethodDescriptor(streamClass)))
                       && TypeUtils.hasSuperClass(insn.owner, Type.getInternalName(Collection.class))) {
                //TODO: a better way of determining whether the input collection is sized
                int knownSpliteratorCharacteristics = 0;

                if (TypeUtils.hasSuperClass(insn.owner, Type.getInternalName(List.class))) {
                    knownSpliteratorCharacteristics |= Spliterator.ORDERED;
                }
                if (TypeUtils.hasSuperClass(insn.owner, Type.getInternalName(Set.class))) {
                    knownSpliteratorCharacteristics |= Spliterator.DISTINCT;
                }

                return Optional.of(new Source.OfCollection(insn, knownSpliteratorCharacteristics));
            }
        }
        return Optional.empty();
    }

    private static Optional<? extends IntermediateOp> tryMapIntermediateOp(MethodNode methodNode, MethodInsnNode insn) {
        if (isStreamType(insn.owner)) {
            Type streamClass = Type.getObjectType(insn.owner);
            Type elementType = getRawElementType(streamClass);

            PerElementTypeInfo info = ELEMENT_TYPE_INFO.get(elementType);

            if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "filter",
                    Type.getMethodDescriptor(streamClass, info.elementPredicateType))) {
                return Optional.of(new IntermediateOp.Filter(insn));
            }
            for (PerElementTypeInfo.PerResultMapTypeInfo mapInfo : info.mapInfo.values()) {
                if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, mapInfo.mapMethodName,
                        Type.getMethodDescriptor(getStreamTypeFromElementType(mapInfo.mappedElementType), mapInfo.mapFunctionType))) {
                    return Optional.of(new IntermediateOp.MapTo(insn, mapInfo.mappedElementType));
                }
            }
            for (PerElementTypeInfo.PerResultMapTypeInfo flatMapInfo : info.flatMapInfo.values()) {
                if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, flatMapInfo.mapMethodName,
                        Type.getMethodDescriptor(getStreamTypeFromElementType(flatMapInfo.mappedElementType), flatMapInfo.mapFunctionType))) {
                    return Optional.of(new IntermediateOp.FlatMapTo(insn, flatMapInfo.mappedElementType));
                }
            }
            if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "distinct", Type.getMethodDescriptor(streamClass))) {
                return Optional.of(new IntermediateOp.Distinct(insn));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "sorted", Type.getMethodDescriptor(streamClass))) {
                return Optional.of(new IntermediateOp.Sorted(insn, false));
            } else if (info.comparatorArray.length != 0
                       && BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "sorted", Type.getMethodDescriptor(streamClass, info.comparatorArray))) {
                return Optional.of(new IntermediateOp.Sorted(insn, true));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "peek", Type.getMethodDescriptor(streamClass, info.elementConsumerType))) {
                return Optional.of(new IntermediateOp.Peek(insn));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "limit", Type.getMethodDescriptor(streamClass, Type.LONG_TYPE))) {
                return Optional.of(new IntermediateOp.Limit(insn));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "skip", Type.getMethodDescriptor(streamClass, Type.LONG_TYPE))) {
                return Optional.of(new IntermediateOp.Skip(insn));
            }
            for (PerElementTypeInfo.PerResultConvertTypeInfo convertInfo : info.convertInfo.values()) {
                if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, convertInfo.convertMethodName,
                        Type.getMethodDescriptor(getStreamTypeFromElementType(convertInfo.convertedElementType)))) {
                    return Optional.of(new IntermediateOp.AsConverted(insn, convertInfo.convertInsn));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<? extends TerminalOp> tryMapTerminalOp(MethodNode methodNode, MethodInsnNode insn) {
        if (isStreamType(insn.owner)) {
            Type streamClass = Type.getObjectType(insn.owner);
            Type elementType = getRawElementType(streamClass);
            PerElementTypeInfo info = ELEMENT_TYPE_INFO.get(elementType);

            if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "forEach", Type.getMethodDescriptor(Type.VOID_TYPE, info.elementConsumerType))) {
                return Optional.of(new TerminalOp.ForEach(insn, false));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "forEachOrdered", Type.getMethodDescriptor(Type.VOID_TYPE, info.elementConsumerType))) {
                return Optional.of(new TerminalOp.ForEach(insn, true));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "toArray", Type.getMethodDescriptor(info.elementArrayType))) {
                return Optional.of(new TerminalOp.ToArray(insn, false));
            } else if (BytecodeHelper.isReference(elementType)
                       && BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "toArray", Type.getMethodDescriptor(info.elementArrayType, Type.getType(IntFunction.class)))) {
                return Optional.of(new TerminalOp.ToArray(insn, true));
            }
            //TODO: reduce, collect
            else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "min", Type.getMethodDescriptor(info.elementOptionalType, info.comparatorArray))) {
                return Optional.of(new TerminalOp.MinMax(insn, false));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "max", Type.getMethodDescriptor(info.elementOptionalType, info.comparatorArray))) {
                return Optional.of(new TerminalOp.MinMax(insn, true));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "count", Type.getMethodDescriptor(Type.LONG_TYPE))) {
                return Optional.of(new TerminalOp.Count(insn));
            } else if (!BytecodeHelper.isReference(elementType)
                       && BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "average", Type.getMethodDescriptor(Type.getType(OptionalDouble.class)))) {
                return Optional.of(new TerminalOp.Average(insn));
            } else if (!BytecodeHelper.isReference(elementType)
                       && BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "summaryStatistics", Type.getMethodDescriptor(info.elementSummaryStatisticsType))) {
                return Optional.of(new TerminalOp.SummaryStatistics(insn));
            } else if (!BytecodeHelper.isReference(elementType)
                       && BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "sum", Type.getMethodDescriptor(elementType))) {
                return Optional.of(new TerminalOp.Sum(insn));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "anyMatch", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, info.elementPredicateType))) {
                return Optional.of(new TerminalOp.Match(insn, true, false));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "allMatch", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, info.elementPredicateType))) {
                return Optional.of(new TerminalOp.Match(insn, false, true));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "noneMatch", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, info.elementPredicateType))) {
                return Optional.of(new TerminalOp.Match(insn, true, true));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "findFirst", Type.getMethodDescriptor(info.elementOptionalType))) {
                return Optional.of(new TerminalOp.Find(insn, true));
            } else if (BytecodeHelper.isINVOKEINTERFACE(insn, insn.owner, "findAny", Type.getMethodDescriptor(info.elementOptionalType))) {
                return Optional.of(new TerminalOp.Find(insn, false));
            }
        }
        return Optional.empty();
    }

    @Override
    public int transformClass(String name, String transformedName, ClassNode classNode) {
        int changedFlags = 0;
        for (MethodNode methodNode : classNode.methods) {
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insn = itr.next();
                TerminalOp terminalOp;
                if (!(insn instanceof MethodInsnNode) || (terminalOp = tryMapTerminalOp(methodNode, (MethodInsnNode) insn).orElse(null)) == null) {
                    continue;
                }

                changedFlags |= transformStreamCall(classNode, methodNode, terminalOp);
            }
        }
        return changedFlags;
    }

    private static int transformStreamCall(ClassNode classNode, MethodNode methodNode, TerminalOp terminalOp) {
        Frame<SourceValue>[] sourceFrames = BytecodeHelper.analyzeSources(classNode.name, methodNode);

        //walk backwards up the stream call chain to determine the whole stream call sequence
        List<IntermediateOp> intermediateOps = new ArrayList<>();
        Source sourceOp = null;
        for (MethodInsnNode lastStageInsn = terminalOp.stageInsn; ; ) {
            Set<AbstractInsnNode> sources = BytecodeHelper.getStackValueFromTop(
                    sourceFrames[methodNode.instructions.indexOf(lastStageInsn)], Type.getArgumentTypes(lastStageInsn.desc).length).insns;
            if (sources.size() != 1) {
                PPatchesMod.LOGGER.error("Not optimizing stream usage at L{};{}{} (stream has {} source instructions)",
                        classNode.name, methodNode.name, methodNode.desc, sources.size());
                return 0;
            }

            AbstractInsnNode rawSourceInsn = sources.iterator().next();
            if (!(rawSourceInsn instanceof MethodInsnNode)) {
                PPatchesMod.LOGGER.error("Not optimizing stream usage at L{};{}{} (expected chained stream calls, but source instruction is '{}')",
                        classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.toString(rawSourceInsn));
                return 0;
            }

            lastStageInsn = (MethodInsnNode) rawSourceInsn;

            Optional<? extends IntermediateOp> optionalIntermediate = tryMapIntermediateOp(methodNode, lastStageInsn);
            if (optionalIntermediate.isPresent()) {
                intermediateOps.add(0, optionalIntermediate.get());
                continue;
            }

            Optional<? extends Source> optionalSource = tryMapSourceStage(methodNode, (MethodInsnNode) rawSourceInsn);
            if (optionalSource.isPresent()) {
                sourceOp = optionalSource.get();
                break;
            }

            PPatchesMod.LOGGER.error("Not optimizing stream usage at L{};{}{} (encountered unexpected instruction '{}')",
                    classNode.name, methodNode.name, methodNode.desc, BytecodeHelper.toString(rawSourceInsn));
            return 0;
        }

        List<Stage> allStages = new ArrayList<>(intermediateOps.size() + 2);
        allStages.add(sourceOp);
        allStages.addAll(intermediateOps);
        allStages.add(terminalOp);
        @SuppressWarnings("unchecked")
        List<ProducerStage> producerStages = (List<ProducerStage>) (Object) allStages.subList(0, allStages.size() - 1);
        @SuppressWarnings("unchecked")
        List<ConsumerStage> consumerStages = (List<ConsumerStage>) (Object) allStages.subList(1, allStages.size());

        //link stages together
        for (int i = 0; i < allStages.size(); i++) {
            if (i != 0) {
                ((ConsumerStage) allStages.get(i)).setPreviousStage((ProducerStage) allStages.get(i - 1));
            }
            if (i != allStages.size() - 1) {
                ((ProducerStage) allStages.get(i)).setNextStage((ConsumerStage) allStages.get(i + 1));
            }
        }

        //allow stages to pre-transform their operands and save them onto the stack
        MutableInt newMaxLocals = new MutableInt(methodNode.maxLocals);

        List<AbstractInsnNode> removeInsns = new ArrayList<>();
        List<InsnList> allTransformInsns = new ArrayList<>(allStages.size());
        for (Stage stage : allStages) {
            List<Type> operandTypes = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(stage.stageInsn().desc)));
            if (stage instanceof Source && stage.stageInsn().getOpcode() != INVOKESTATIC) {
                operandTypes.add(0, Type.getObjectType(stage.stageInsn().owner));
            }

            InsnList transformInsns = new InsnList();
            List<LVTReference> operandValues = new ArrayList<>();

            //Frame<SourceValue> sourceFrame = sourceFrames[methodNode.instructions.indexOf(stage.stageInsn())];
            for (int i = 0; i < operandTypes.size(); i++) {
                //Set<AbstractInsnNode> rawOperandSourceInsns = BytecodeHelper.getStackValueFromTop(sourceFrame, rawOperandTypes.size() - 1 - i).insns;
                //TODO: optimize when the operand is already stored in a local variable? (what about potential side effects?)

                operandValues.add(new LVTReference(operandTypes.get(i), newMaxLocals.getAndAdd(operandTypes.get(i).getSize())));
            }

            //store operand values into local variables
            for (int i = operandValues.size() - 1; i >= 0; i--) {
                transformInsns.add(operandValues.get(i).makeStore());
            }

            stage.transformOperands(ImmutableList.copyOf(operandTypes), ImmutableList.copyOf(operandValues), transformedOperandType -> {
                int newLvtIndex = newMaxLocals.getAndAdd(transformedOperandType.getSize());
                LVTReference lvtReference = new LVTReference(transformedOperandType, newLvtIndex);
                operandValues.add(lvtReference);
                return lvtReference;
            }, transformInsns, removeInsns);

            allTransformInsns.add(transformInsns);
        }

        //actually generate the bytecode
        InsnList out = new InsnList();
        try {
            LabelNode codeBreakLabel = new LabelNode();
            LabelNode endBreakLabel = new LabelNode();
            LabelNode tailLabel = new LabelNode();
            sourceOp.visitCode(out, new BranchLabels(null, codeBreakLabel, tailLabel), null);
            out.add(codeBreakLabel);
            sourceOp.visitDone(out, endBreakLabel, tailLabel);
            out.add(endBreakLabel);
            terminalOp.finalizeReturnValue(out);
            out.add(tailLabel);
        } catch (UnsupportedOperationException e) {
            PPatchesMod.LOGGER.error("Not optimizing stream usage at L{};{}{} (support for {} not implemented yet)",
                    classNode.name, methodNode.name, methodNode.desc, e.getMessage());
            return 0;
        }

        //if we've gotten this far, we assume everything is going to be fine and apply the changes
        methodNode.maxLocals = newMaxLocals.intValue();

        //insert the main code sequence which emulates the stream behavior
        methodNode.instructions.insert(terminalOp.stageInsn(), out);

        //insert code sequence to save and transform the stack operands for each stage
        for (int i = 0; i < allStages.size(); i++) {
            methodNode.instructions.insert(allStages.get(i).stageInsn(), allTransformInsns.get(i));
        }

        //remove the stream method invocation representing each pipeline stage
        for (Stage stage : allStages) {
            methodNode.instructions.remove(stage.stageInsn());
        }

        //remove any other instructions a stage requested to remove
        for (AbstractInsnNode removeInsn : removeInsns) {
            methodNode.instructions.remove(removeInsn);
        }

        return CHANGED;
    }

    @RequiredArgsConstructor
    @With
    private static class BranchLabels {
        /**
         * If jumped to, immediately stops processing the current element and advances to the next one.
         * <p>
         * This is equivalent to allowing control flow to pass beyond the last instruction produced by {@link Stage#visitCode}.
         */
        public final LabelNode skipLabel;

        /**
         * If jumped to, immediately stops processing the current element, skips all remaining elements, and lets the terminal operation finalize and return the end value.
         */
        public final LabelNode breakLabel;

        /**
         * If jumped to, immediately returns the value currently on the stack without allowing the terminal operation to finalize.
         */
        public final LabelNode returnLabel;
    }

    private interface Stage {
        MethodInsnNode stageInsn();

        void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns);

        default void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
            throw new UnsupportedOperationException("visitCode on " + this.getClass().getTypeName()); //TODO
        }

        void visitDone(InsnList out, LabelNode breakLabel, LabelNode returnLabel);
    }

    private interface ProducerStage extends Stage {
        void setNextStage(ConsumerStage stage);

        int knownSpliteratorCharacteristics();

        default Type producedElementType() {
            return getRawElementType(Type.getReturnType(this.stageInsn().desc));
        }

        void loadExactSize(InsnList out, boolean asInt);
    }

    private interface ConsumerStage extends Stage {
        void setPreviousStage(ProducerStage stage);

        default Type consumedElementType() {
            return getRawElementType(this.stageInsn().owner);
        }

        default PerElementTypeInfo consumedElementTypeInfo() {
            return ELEMENT_TYPE_INFO.get(this.consumedElementType());
        }
    }

    @RequiredArgsConstructor
    private static abstract class AbstractStage implements Stage {
        @Getter
        public final MethodInsnNode stageInsn;

        @Override
        public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
            //no-op
        }
    }

    @Getter
    private static abstract class Source extends AbstractStage implements ProducerStage {
        public ConsumerStage nextStage;
        public final int knownSpliteratorCharacteristics;

        protected LVTReference producedValue;

        public Source(MethodInsnNode stageInsn, int knownSpliteratorCharacteristics) {
            super(stageInsn);
            this.knownSpliteratorCharacteristics = knownSpliteratorCharacteristics;
        }

        @Override
        public void setNextStage(ConsumerStage stage) {
            this.nextStage = stage;
        }

        @Override
        public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
            super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
            this.producedValue = transformedOperandLvtAlloc.apply(this.producedElementType());
        }

        @Override
        public void visitDone(InsnList out, LabelNode breakLabel, LabelNode returnLabel) {
            this.nextStage.visitDone(out, breakLabel, returnLabel);
        }

        @Override
        public void loadExactSize(InsnList out, boolean asInt) {
            throw new UnsupportedOperationException("loadExactSize on " + this.getClass().getTypeName());
        }

        private static class OfEmpty extends Source {
            public OfEmpty(MethodInsnNode stageInsn) {
                super(stageInsn, Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.IMMUTABLE);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                //no-op
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                //no-op
            }

            @Override
            public void loadExactSize(InsnList out, boolean asInt) {
                out.add(new InsnNode(asInt ? ICONST_0 : LCONST_0));
            }
        }

        private static class OfSingle extends Source {
            protected LVTReference singleValue;

            public OfSingle(MethodInsnNode stageInsn) {
                super(stageInsn, Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.IMMUTABLE);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.singleValue = rawOperandValues.get(0);
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                this.nextStage.visitCode(out, labels.withSkipLabel(labels.breakLabel), this.singleValue);
            }

            @Override
            public void loadExactSize(InsnList out, boolean asInt) {
                out.add(new InsnNode(asInt ? ICONST_1 : LCONST_1));
            }
        }

        private static class OfArray extends Source {
            protected LVTReference arrayValue;
            protected LVTReference indexValue;

            public OfArray(MethodInsnNode stageInsn, int knownSpliteratorCharacteristics) {
                super(stageInsn, knownSpliteratorCharacteristics | Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.IMMUTABLE);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.arrayValue = rawOperandValues.get(0);
                this.indexValue = transformedOperandLvtAlloc.apply(Type.INT_TYPE);
                transformInsns.add(new InsnNode(ICONST_0));
                transformInsns.add(this.indexValue.makeStore());
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                // for (int i = 0; i < arr.length; i++) nextStage(arr[i]);

                LabelNode headLabel = new LabelNode();
                LabelNode incrementLabel = new LabelNode();

                out.add(headLabel);
                out.add(this.indexValue.makeLoad());
                out.add(this.arrayValue.makeLoad());
                out.add(new InsnNode(ARRAYLENGTH));
                out.add(new JumpInsnNode(IF_ICMPGE, labels.breakLabel));
                out.add(this.arrayValue.makeLoad());
                out.add(this.indexValue.makeLoad());
                out.add(new InsnNode(this.producedElementType().getOpcode(IALOAD)));
                out.add(this.producedValue.makeStore());
                this.nextStage.visitCode(out, labels.withSkipLabel(incrementLabel), this.producedValue);
                out.add(incrementLabel);
                out.add(new IincInsnNode(this.indexValue.var(), 1));
                out.add(new JumpInsnNode(GOTO, headLabel));
            }

            @Override
            public void loadExactSize(InsnList out, boolean asInt) {
                out.add(this.arrayValue.makeLoad());
                out.add(new InsnNode(ARRAYLENGTH));
                if (!asInt) {
                    out.add(new InsnNode(I2L));
                }
            }
        }

        private static class OfArraySubsequence extends Source {
            public OfArraySubsequence(MethodInsnNode stageInsn, int knownSpliteratorCharacteristics) {
                super(stageInsn, knownSpliteratorCharacteristics | Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.IMMUTABLE);
            }

            //TODO
        }

        private static class OfRange extends Source {
            public final boolean closed;

            protected LVTReference minValue;
            protected LVTReference maxValue;
            protected LVTReference currentValue;

            public OfRange(MethodInsnNode stageInsn, boolean closed) {
                super(stageInsn, Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.SORTED);
                this.closed = closed;
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.minValue = rawOperandValues.get(0);
                this.maxValue = rawOperandValues.get(1);

                this.currentValue = transformedOperandLvtAlloc.apply(rawOperandTypes.get(0));
                transformInsns.add(this.minValue.makeLoad());
                transformInsns.add(this.currentValue.makeStore());
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                // for (; min < max; min++) nextStage(min);

                LabelNode headLabel = new LabelNode();
                LabelNode incrementLabel = new LabelNode();

                out.add(headLabel);
                out.add(this.currentValue.makeLoad());
                out.add(this.maxValue.makeLoad());
                if (this.producedElementType().equals(Type.INT_TYPE)) {
                    out.add(new JumpInsnNode(this.closed ? IF_ICMPGT : IF_ICMPGE, labels.breakLabel));
                } else {
                    out.add(new InsnNode(LCMP));
                    out.add(new JumpInsnNode(this.closed ? IFGT : IFGE, labels.breakLabel));
                }
                this.nextStage.visitCode(out, labels.withSkipLabel(incrementLabel), this.currentValue);
                out.add(incrementLabel);
                if (this.producedElementType().equals(Type.INT_TYPE)) {
                    out.add(new IincInsnNode(this.currentValue.var(), 1));
                } else {
                    out.add(this.currentValue.makeLoad());
                    out.add(new InsnNode(LCONST_1));
                    out.add(new InsnNode(LADD));
                    out.add(this.currentValue.makeStore());
                }
                out.add(new JumpInsnNode(GOTO, headLabel));
            }

            @Override
            public void loadExactSize(InsnList out, boolean asInt) {
                out.add(this.maxValue.makeLoad());
                out.add(this.minValue.makeLoad());
                out.add(new InsnNode(this.producedElementType().getOpcode(ISUB)));
                if (this.closed) {
                    out.add(new InsnNode(this.producedElementType().equals(Type.INT_TYPE) ? ICONST_1 : LCONST_1));
                    out.add(new InsnNode(this.producedElementType().getOpcode(IADD)));
                }

                out.add(BytecodeHelper.loadConstantDefaultValueInsn(this.producedElementType()));
                out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Math.class), "max", Type.getMethodDescriptor(this.producedElementType(), this.producedElementType(), this.producedElementType()), false));

                if (this.producedElementType().equals(Type.INT_TYPE)) {
                    if (!asInt) {
                        out.add(new InsnNode(I2L));
                    }
                } else if (asInt) {
                    out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Math.class), "toIntExact", "(J)I", false));
                }
            }
        }

        private static class OfCollection extends Source {
            protected LVTReference collectionValue;
            protected LVTReference iteratorValue;

            public OfCollection(MethodInsnNode stageInsn, int knownSpliteratorCharacteristics) {
                super(stageInsn, knownSpliteratorCharacteristics);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.collectionValue = rawOperandValues.get(0);
                this.iteratorValue = transformedOperandLvtAlloc.apply(Type.getType(Iterator.class));
                transformInsns.add(this.collectionValue.makeLoad());
                transformInsns.add(new MethodInsnNode(INVOKEINTERFACE, Type.getInternalName(Collection.class), "iterator", Type.getMethodDescriptor(Type.getType(Iterator.class)), true));
                transformInsns.add(this.iteratorValue.makeStore());
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                // while (iterator.hasNext()) nextStage(iterator.next());

                LabelNode headLabel = new LabelNode();

                out.add(headLabel);
                out.add(this.iteratorValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEINTERFACE, Type.getInternalName(Iterator.class), "hasNext", "()Z", true));
                out.add(new JumpInsnNode(IFEQ, labels.breakLabel));
                out.add(this.iteratorValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEINTERFACE, Type.getInternalName(Iterator.class), "next", "()Ljava/lang/Object;", true));
                out.add(this.producedValue.makeStore());
                this.nextStage.visitCode(out, labels.withSkipLabel(headLabel), this.producedValue);
                out.add(new JumpInsnNode(GOTO, headLabel));
            }

            @Override
            public void loadExactSize(InsnList out, boolean asInt) {
                out.add(this.collectionValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEINTERFACE, Type.getInternalName(Collection.class), "size", "()I", true));
                if (!asInt) {
                    out.add(new InsnNode(I2L));
                }
            }
        }

        private static class OfIterator extends Source {
            public OfIterator(MethodInsnNode stageInsn, int knownSpliteratorCharacteristics) {
                super(stageInsn, knownSpliteratorCharacteristics);
            }

            //TODO
        }

        private static class OfSpliterator extends Source {
            public OfSpliterator(MethodInsnNode stageInsn, int knownSpliteratorCharacteristics) {
                super(stageInsn, knownSpliteratorCharacteristics);
            }

            //TODO
        }
    }

    private static abstract class IntermediateOp extends AbstractStage implements ProducerStage, ConsumerStage {
        public ProducerStage previousStage;
        public ConsumerStage nextStage;

        protected final boolean doesProduceValue;
        protected LVTReference producedValue;

        public IntermediateOp(MethodInsnNode stageInsn, boolean doesProduceValue) {
            super(stageInsn);
            this.doesProduceValue = doesProduceValue;
        }

        @Override
        public int knownSpliteratorCharacteristics() {
            return this.previousStage.knownSpliteratorCharacteristics();
        }

        @Override
        public void setPreviousStage(ProducerStage stage) {
            this.previousStage = stage;
        }

        @Override
        public void setNextStage(ConsumerStage stage) {
            this.nextStage = stage;
        }

        @Override
        public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
            super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
            if (this.doesProduceValue) {
                this.producedValue = transformedOperandLvtAlloc.apply(this.producedElementType());
            }
        }

        @Override
        public void visitDone(InsnList out, LabelNode breakLabel, LabelNode returnLabel) {
            this.nextStage.visitDone(out, breakLabel, returnLabel);
        }

        @Override
        public void loadExactSize(InsnList out, boolean asInt) {
            Preconditions.checkState((this.knownSpliteratorCharacteristics() & Spliterator.SIZED) != 0);
            this.previousStage.loadExactSize(out, asInt);
        }

        private static class Filter extends IntermediateOp {
            protected LVTReference predicateValue;

            public Filter(MethodInsnNode stageInsn) {
                super(stageInsn, false);
            }

            @Override
            public int knownSpliteratorCharacteristics() {
                return super.knownSpliteratorCharacteristics() & ~(Spliterator.SIZED | Spliterator.SUBSIZED);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.predicateValue = rawOperandValues.get(0);
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();

                out.add(this.predicateValue.makeLoad());
                out.add(consumedValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEINTERFACE, info.elementPredicateType.getInternalName(), "test", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, info.elementType), true));
                out.add(new JumpInsnNode(IFEQ, labels.skipLabel));
                this.nextStage.visitCode(out, labels, consumedValue);
            }
        }

        private static class MapTo extends IntermediateOp {
            public final Type mappedType;

            protected LVTReference mapperValue;

            public MapTo(MethodInsnNode stageInsn, Type mappedType) {
                super(stageInsn, true);
                this.mappedType = mappedType;
            }

            @Override
            public int knownSpliteratorCharacteristics() {
                return super.knownSpliteratorCharacteristics() & ~(Spliterator.SORTED | Spliterator.DISTINCT | Spliterator.NONNULL);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.mapperValue = rawOperandValues.get(0);
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();
                PerElementTypeInfo.PerResultMapTypeInfo mapInfo = info.mapInfo.get(this.mappedType);

                out.add(this.mapperValue.makeLoad());
                out.add(consumedValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEINTERFACE, mapInfo.mapFunctionType.getInternalName(), mapInfo.mapFunctionApplyMethodName, Type.getMethodDescriptor(this.mappedType, info.elementType), true));
                out.add(this.producedValue.makeStore());
                this.nextStage.visitCode(out, labels, this.producedValue);
            }
        }

        private static class FlatMapTo extends IntermediateOp {
            public final Type mappedType;

            public FlatMapTo(MethodInsnNode stageInsn, Type mappedType) {
                super(stageInsn, true);
                this.mappedType = mappedType;
            }

            @Override
            public int knownSpliteratorCharacteristics() {
                return super.knownSpliteratorCharacteristics() & ~(Spliterator.SORTED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.SIZED | Spliterator.SUBSIZED);
            }

            //TODO
        }

        private static class Distinct extends IntermediateOp {
            public Distinct(MethodInsnNode stageInsn) {
                super(stageInsn, true);
            }

            @Override
            public int knownSpliteratorCharacteristics() {
                int previous = super.knownSpliteratorCharacteristics();
                return (previous & Spliterator.DISTINCT) != 0
                        ? previous
                        : previous & ~(Spliterator.SIZED | Spliterator.SUBSIZED) | Spliterator.DISTINCT;
            }

            //TODO
        }

        private static class Sorted extends IntermediateOp {
            public final boolean hasComparator;

            protected LVTReference comparatorValue;

            protected Type listType;
            protected LVTReference listValue;
            protected LVTReference indexValue;

            public Sorted(MethodInsnNode stageInsn, boolean hasComparator) {
                super(stageInsn, true);
                this.hasComparator = hasComparator;
            }

            @Override
            public int knownSpliteratorCharacteristics() {
                int previous = super.knownSpliteratorCharacteristics();
                return (previous & Spliterator.SORTED) != 0
                        ? previous
                        : previous | (Spliterator.SORTED | Spliterator.ORDERED);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                if (this.hasComparator) {
                    this.comparatorValue = rawOperandValues.get(0);
                }

                this.listType = this.consumedElementTypeInfo().elementArrayListType;
                this.listValue = transformedOperandLvtAlloc.apply(this.listType);
                transformInsns.add(new TypeInsnNode(NEW, this.listType.getInternalName()));
                transformInsns.add(new InsnNode(DUP));
                if ((this.previousStage.knownSpliteratorCharacteristics() & Spliterator.SIZED) != 0) { //we can preallocate the backing array exactly
                    this.previousStage.loadExactSize(transformInsns, true);
                    transformInsns.add(new MethodInsnNode(INVOKESPECIAL, this.listType.getInternalName(), "<init>", "(I)V", false));
                } else {
                    transformInsns.add(new MethodInsnNode(INVOKESPECIAL, this.listType.getInternalName(), "<init>", "()V", false));
                }
                transformInsns.add(this.listValue.makeStore());

                this.indexValue = transformedOperandLvtAlloc.apply(Type.INT_TYPE);
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                // list.add(consumedValue);

                out.add(this.listValue.makeLoad());
                out.add(consumedValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "add", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, this.consumedElementType()), false));
                out.add(new InsnNode(POP));
            }

            @Override
            public void visitDone(InsnList out, LabelNode breakLabel, LabelNode returnLabel) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();

                //sort the array
                out.add(this.listValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "elements", Type.getMethodDescriptor(info.elementArrayType), false));
                out.add(new InsnNode(ICONST_0));
                out.add(this.listValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "size", "()I", false));
                if (this.hasComparator) {
                    out.add(this.comparatorValue.makeLoad());
                    out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Arrays.class), "sort", Type.getMethodDescriptor(Type.VOID_TYPE, info.elementArrayType, Type.INT_TYPE, Type.INT_TYPE, Type.getType(Comparator.class)), false));
                } else {
                    out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Arrays.class), "sort", Type.getMethodDescriptor(Type.VOID_TYPE, info.elementArrayType, Type.INT_TYPE, Type.INT_TYPE), false));
                }

                //iterate over the elements and pass them to the next stage
                // for (int i = 0; i < list.size(); i++) nextStage(list.get(i));

                out.add(new InsnNode(ICONST_0));
                out.add(this.indexValue.makeStore());

                LabelNode headLabel = new LabelNode();
                LabelNode incrementLabel = new LabelNode();

                out.add(headLabel);
                out.add(this.indexValue.makeLoad());
                out.add(this.listValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "size", "()I", false));
                out.add(new JumpInsnNode(IF_ICMPGE, breakLabel));
                out.add(this.listValue.makeLoad());
                out.add(this.indexValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "get", Type.getMethodDescriptor(this.consumedElementType(), Type.INT_TYPE), false));
                out.add(this.producedValue.makeStore());
                this.nextStage.visitCode(out, new BranchLabels(incrementLabel, breakLabel, returnLabel), this.producedValue);
                out.add(incrementLabel);
                out.add(new IincInsnNode(this.indexValue.var(), 1));
                out.add(new JumpInsnNode(GOTO, headLabel));

                super.visitDone(out, breakLabel, returnLabel);
            }
        }

        private static class Peek extends IntermediateOp {
            protected LVTReference actionValue;

            public Peek(MethodInsnNode stageInsn) {
                super(stageInsn, false);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.actionValue = rawOperandValues.get(0);
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();

                out.add(this.actionValue.makeLoad());
                out.add(consumedValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEINTERFACE, info.elementConsumerType.getInternalName(), "accept", Type.getMethodDescriptor(Type.VOID_TYPE, info.elementType), true));
                this.nextStage.visitCode(out, labels, consumedValue);
            }
        }

        private static class Limit extends IntermediateOp {
            protected LVTReference maxSizeValue;
            protected LVTReference currValue;

            public Limit(MethodInsnNode stageInsn) {
                super(stageInsn, false);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.maxSizeValue = rawOperandValues.get(0);
                this.currValue = transformedOperandLvtAlloc.apply(Type.LONG_TYPE);
                transformInsns.add(new InsnNode(LCONST_0));
                transformInsns.add(this.currValue.makeStore());

                //TODO: throw exception if maxSize is negative
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                // if (curr >= maxSize) break; else { curr++; nextStage(consumedValue); }

                out.add(this.currValue.makeLoad());
                out.add(this.maxSizeValue.makeLoad());
                out.add(new InsnNode(LCMP));
                out.add(new JumpInsnNode(IFGE, labels.breakLabel));
                out.add(this.currValue.makeLoad());
                out.add(new InsnNode(LCONST_1));
                out.add(new InsnNode(LADD));
                out.add(this.currValue.makeStore());
                this.nextStage.visitCode(out, labels, consumedValue);
            }

            @Override
            public void loadExactSize(InsnList out, boolean asInt) {
                super.loadExactSize(out, false);

                out.add(this.maxSizeValue.makeLoad());
                out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Math.class), "min", "(JJ)J", false));
                if (asInt) {
                    out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Math.class), "toIntExact", "(J)I", false));
                }
            }
        }

        private static class Skip extends IntermediateOp {
            protected LVTReference nValue;
            protected LVTReference currValue;

            public Skip(MethodInsnNode stageInsn) {
                super(stageInsn, false);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.nValue = rawOperandValues.get(0);
                this.currValue = transformedOperandLvtAlloc.apply(Type.LONG_TYPE);
                transformInsns.add(new InsnNode(LCONST_0));
                transformInsns.add(this.currValue.makeStore());

                //TODO: throw exception if n is negative
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                // if (curr >= n) nextStage(consumedValue); else curr++;

                LabelNode passLabel = new LabelNode();

                out.add(this.currValue.makeLoad());
                out.add(this.nValue.makeLoad());
                out.add(new InsnNode(LCMP));
                out.add(new JumpInsnNode(IFGE, passLabel));
                out.add(this.currValue.makeLoad());
                out.add(new InsnNode(LCONST_1));
                out.add(new InsnNode(LADD));
                out.add(this.currValue.makeStore());
                out.add(new JumpInsnNode(GOTO, labels.skipLabel));
                out.add(passLabel);
                this.nextStage.visitCode(out, labels, consumedValue);
            }

            @Override
            public void loadExactSize(InsnList out, boolean asInt) {
                super.loadExactSize(out, false);

                out.add(this.nValue.makeLoad());
                out.add(new InsnNode(LSUB));
                out.add(new InsnNode(LCONST_0));
                out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Math.class), "max", "(JJ)J", false));
                if (asInt) {
                    out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Math.class), "toIntExact", "(J)I", false));
                }
            }
        }

        private static class AsConverted extends IntermediateOp {
            public final AbstractInsnNode convertInsn;

            public AsConverted(MethodInsnNode stageInsn, AbstractInsnNode convertInsn) {
                super(stageInsn, true);
                this.convertInsn = convertInsn;
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                out.add(consumedValue.makeLoad());
                out.add(this.convertInsn.clone(null));
                out.add(this.producedValue.makeStore());
                this.nextStage.visitCode(out, labels, this.producedValue);
            }
        }
    }

    private static abstract class TerminalOp extends AbstractStage implements ConsumerStage {
        public ProducerStage previousStage;

        public TerminalOp(MethodInsnNode stageInsn) {
            super(stageInsn);
        }

        @Override
        public void setPreviousStage(ProducerStage stage) {
            this.previousStage = stage;
        }

        public void finalizeReturnValue(InsnList out) { //TODO: implement
            throw new UnsupportedOperationException(this.getClass().getTypeName());
        }

        @Override
        public void visitDone(InsnList out, LabelNode breakLabel, LabelNode returnLabel) {
            //no-op
        }

        private static class ForEach extends TerminalOp {
            public final boolean ordered;

            protected LVTReference actionValue;

            public ForEach(MethodInsnNode stageInsn, boolean ordered) {
                super(stageInsn);
                this.ordered = ordered;
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.actionValue = rawOperandValues.get(0);
            }

            @Override
            public void finalizeReturnValue(InsnList out) {
                //no-op
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();

                out.add(this.actionValue.makeLoad());
                out.add(consumedValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEINTERFACE, info.elementConsumerType.getInternalName(), "accept", Type.getMethodDescriptor(Type.VOID_TYPE, info.elementType), true));
            }
        }

        private static class ToArray extends TerminalOp {
            public final boolean hasGenerator;

            protected LVTReference generatorValue;

            protected Type listType;
            protected LVTReference listValue;

            protected LVTReference fixedArrayValue;
            protected LVTReference writeIndexValue;

            public ToArray(MethodInsnNode stageInsn, boolean hasGenerator) {
                super(stageInsn);
                this.hasGenerator = hasGenerator;
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                if (this.hasGenerator) {
                    this.generatorValue = rawOperandValues.get(0);
                }

                PerElementTypeInfo info = this.consumedElementTypeInfo();

                if ((this.previousStage.knownSpliteratorCharacteristics() & Spliterator.SIZED) != 0) { //we can preallocate the backing array exactly
                    this.fixedArrayValue = transformedOperandLvtAlloc.apply(info.elementArrayType);
                    if (this.hasGenerator) {
                        transformInsns.add(this.generatorValue.makeLoad());
                        this.previousStage.loadExactSize(transformInsns, true);
                        transformInsns.add(new MethodInsnNode(INVOKEINTERFACE, Type.getInternalName(IntFunction.class), "apply", Type.getMethodDescriptor(Type.getType(Object.class), Type.INT_TYPE), true));
                        transformInsns.add(new TypeInsnNode(CHECKCAST, info.elementArrayType.getInternalName()));
                    } else {
                        this.previousStage.loadExactSize(transformInsns, true);
                        transformInsns.add(BytecodeHelper.generateNewArray(info.elementType));
                    }
                    transformInsns.add(this.fixedArrayValue.makeStore());

                    this.writeIndexValue = transformedOperandLvtAlloc.apply(Type.INT_TYPE);
                    transformInsns.add(new InsnNode(ICONST_0));
                    transformInsns.add(this.writeIndexValue.makeStore());
                } else {
                    //TODO: i'd like to use some kind of spined buffer in this case
                    this.listType = this.consumedElementTypeInfo().elementArrayListType;
                    this.listValue = transformedOperandLvtAlloc.apply(this.listType);
                    transformInsns.add(new TypeInsnNode(NEW, this.listType.getInternalName()));
                    transformInsns.add(new InsnNode(DUP));
                    transformInsns.add(new MethodInsnNode(INVOKESPECIAL, this.listType.getInternalName(), "<init>", "()V", false));
                    transformInsns.add(this.listValue.makeStore());
                }
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                if ((this.previousStage.knownSpliteratorCharacteristics() & Spliterator.SIZED) != 0) {
                    // { fixedArray[writeIndex] = consumedValue; writeIndex++; }

                    out.add(this.fixedArrayValue.makeLoad());
                    out.add(this.writeIndexValue.makeLoad());
                    out.add(consumedValue.makeLoad());
                    out.add(new InsnNode(this.consumedElementType().getOpcode(IASTORE)));
                    out.add(new IincInsnNode(this.writeIndexValue.var(), 1));
                } else {
                    // list.add(consumedValue);

                    out.add(this.listValue.makeLoad());
                    out.add(consumedValue.makeLoad());
                    out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "add", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, this.consumedElementType()), false));
                    out.add(new InsnNode(POP));
                }
            }

            @Override
            public void finalizeReturnValue(InsnList out) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();

                if ((this.previousStage.knownSpliteratorCharacteristics() & Spliterator.SIZED) != 0) {
                    // { if (writeIndex != fixedArray.length) { throw new IllegalStateException(); } return fixedArray; }
                    LabelNode tailLabel = new LabelNode();

                    out.add(this.writeIndexValue.makeLoad());
                    out.add(this.fixedArrayValue.makeLoad());
                    out.add(new InsnNode(ARRAYLENGTH));
                    out.add(new JumpInsnNode(IF_ICMPEQ, tailLabel));
                    out.add(InvokeDynamicUtils.makeNewException(IllegalStateException.class, "Current size less than fixed size"));
                    out.add(new InsnNode(ATHROW));
                    out.add(tailLabel);
                    out.add(this.fixedArrayValue.makeLoad());
                } else {
                    if (this.hasGenerator) {
                        // list.toArray((Object[]) generator.apply(list.size()));
                        out.add(this.listValue.makeLoad());
                        out.add(this.generatorValue.makeLoad());
                        out.add(this.listValue.makeLoad());
                        out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "size", Type.getMethodDescriptor(Type.INT_TYPE), false));
                        out.add(new MethodInsnNode(INVOKEINTERFACE, Type.getInternalName(IntFunction.class), "apply", Type.getMethodDescriptor(Type.getType(Object.class), Type.INT_TYPE), true));
                        out.add(new TypeInsnNode(CHECKCAST, info.elementArrayType.getInternalName()));
                        out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "toArray", Type.getMethodDescriptor(info.elementArrayType, info.elementArrayType), false));
                    } else {
                        // (list.elements().length == list.size() ? list.elements() : Arrays.copyOf(list.elements(), list.size()))

                        LabelNode tailLabel = new LabelNode();

                        out.add(this.listValue.makeLoad());
                        out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "elements", Type.getMethodDescriptor(info.elementArrayType), false));
                        out.add(new InsnNode(DUP));
                        out.add(new InsnNode(ARRAYLENGTH));
                        out.add(this.listValue.makeLoad());
                        out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "size", Type.getMethodDescriptor(Type.INT_TYPE), false));
                        out.add(new JumpInsnNode(IF_ICMPEQ, tailLabel));

                        out.add(this.listValue.makeLoad());
                        out.add(new MethodInsnNode(INVOKEVIRTUAL, this.listType.getInternalName(), "size", Type.getMethodDescriptor(Type.INT_TYPE), false));
                        out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Arrays.class), "copyOf", Type.getMethodDescriptor(info.elementArrayType, info.elementArrayType, Type.INT_TYPE), false));

                        out.add(tailLabel);
                    }
                }
            }
        }

        private static class Reduce extends TerminalOp {
            public final boolean hasIdentity;
            public final boolean hasCombiner;

            public Reduce(MethodInsnNode stageInsn, boolean hasIdentity, boolean hasCombiner) {
                super(stageInsn);
                this.hasIdentity = hasIdentity;
                this.hasCombiner = hasCombiner;
            }

            //TODO
        }

        private static class Collect extends TerminalOp {
            public final boolean hasCollector;

            public Collect(MethodInsnNode stageInsn, boolean hasCollector) {
                super(stageInsn);
                this.hasCollector = hasCollector;
            }

            //TODO
        }

        private static class MinMax extends TerminalOp {
            public final boolean isMin; //if false, then this is max()

            public MinMax(MethodInsnNode stageInsn, boolean isMin) {
                super(stageInsn);
                this.isMin = isMin;
            }

            //TODO
        }

        private static class Count extends TerminalOp {
            protected LVTReference counterValue;

            public Count(MethodInsnNode stageInsn) {
                super(stageInsn);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.counterValue = transformedOperandLvtAlloc.apply(Type.LONG_TYPE);
                transformInsns.add(new InsnNode(LCONST_0));
                transformInsns.add(this.counterValue.makeStore());
            }

            @Override
            public void finalizeReturnValue(InsnList out) {
                out.add(this.counterValue.makeLoad());
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                out.add(this.counterValue.makeLoad());
                out.add(new InsnNode(LCONST_1));
                out.add(new InsnNode(LADD));
                out.add(this.counterValue.makeStore());
            }
        }

        private static class Average extends TerminalOp {
            protected final Type sumType;
            protected LVTReference sumValue;
            protected LVTReference countValue;

            public Average(MethodInsnNode stageInsn) {
                super(stageInsn);
                switch (this.consumedElementType().getSort()) {
                    case Type.INT:
                    case Type.LONG:
                        this.sumType = Type.LONG_TYPE;
                        break;
                    case Type.DOUBLE:
                        this.sumType = Type.DOUBLE_TYPE;
                        break;
                    default:
                        throw new IllegalStateException("called average() on " + this.stageInsn.owner);
                }
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.sumValue = transformedOperandLvtAlloc.apply(this.sumType);
                transformInsns.add(BytecodeHelper.loadConstantDefaultValueInsn(this.sumType));
                transformInsns.add(this.sumValue.makeStore());
                this.countValue = transformedOperandLvtAlloc.apply(Type.LONG_TYPE);
                transformInsns.add(new InsnNode(LCONST_0));
                transformInsns.add(this.countValue.makeStore());
            }

            @Override
            public void finalizeReturnValue(InsnList out) {
                // (count > 0L ? OptionalDouble.empty() : OptionalDouble.of((double) sum / (double) count))

                LabelNode zeroLabel = new LabelNode();
                LabelNode tailLabel = new LabelNode();

                out.add(this.countValue.makeLoad());
                out.add(new InsnNode(LCONST_0));
                out.add(new InsnNode(LCMP));
                out.add(new JumpInsnNode(IFLE, zeroLabel));

                out.add(this.sumValue.makeLoad());
                if (this.sumType.equals(Type.LONG_TYPE)) {
                    out.add(new InsnNode(L2D));
                }
                out.add(this.countValue.makeLoad());
                out.add(new InsnNode(L2D));
                out.add(new InsnNode(DDIV));
                out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(OptionalDouble.class), "of", Type.getMethodDescriptor(Type.getType(OptionalDouble.class), Type.DOUBLE_TYPE), false));
                out.add(new JumpInsnNode(GOTO, tailLabel));

                out.add(zeroLabel);
                out.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(OptionalDouble.class), "empty", Type.getMethodDescriptor(Type.getType(OptionalDouble.class)), false));

                out.add(tailLabel);
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                // { sum += consumedValue; count++; }

                out.add(this.sumValue.makeLoad());
                out.add(consumedValue.makeLoad());
                if (this.consumedElementType().equals(Type.INT_TYPE)) {
                    out.add(new InsnNode(I2L));
                }
                out.add(new InsnNode(this.sumType.getOpcode(IADD)));
                out.add(this.sumValue.makeStore());

                out.add(this.countValue.makeLoad());
                out.add(new InsnNode(LCONST_1));
                out.add(new InsnNode(LADD));
                out.add(this.countValue.makeStore());
            }
        }

        private static class SummaryStatistics extends TerminalOp {
            protected LVTReference statisticsValue;

            public SummaryStatistics(MethodInsnNode stageInsn) {
                super(stageInsn);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);

                PerElementTypeInfo info = this.consumedElementTypeInfo();

                this.statisticsValue = transformedOperandLvtAlloc.apply(info.elementSummaryStatisticsType);
                transformInsns.add(new TypeInsnNode(NEW, info.elementSummaryStatisticsType.getInternalName()));
                transformInsns.add(new InsnNode(DUP));
                transformInsns.add(new MethodInsnNode(INVOKESPECIAL, info.elementSummaryStatisticsType.getInternalName(), "<init>", "()V", false));
                transformInsns.add(this.statisticsValue.makeStore());
            }

            @Override
            public void finalizeReturnValue(InsnList out) {
                out.add(this.statisticsValue.makeLoad());
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();

                out.add(this.statisticsValue.makeLoad());
                out.add(consumedValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEVIRTUAL, info.elementSummaryStatisticsType.getInternalName(), "accept", Type.getMethodDescriptor(Type.VOID_TYPE, info.elementType), false));
            }
        }

        private static class Sum extends TerminalOp {
            protected LVTReference sumValue;

            public Sum(MethodInsnNode stageInsn) {
                super(stageInsn);
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.sumValue = transformedOperandLvtAlloc.apply(this.consumedElementType());
                transformInsns.add(BytecodeHelper.loadConstantDefaultValueInsn(this.consumedElementType()));
                transformInsns.add(this.sumValue.makeStore());
            }

            @Override
            public void finalizeReturnValue(InsnList out) {
                out.add(this.sumValue.makeLoad());
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                // sum += consumedValue;

                out.add(this.sumValue.makeLoad());
                out.add(consumedValue.makeLoad());
                out.add(new InsnNode(this.consumedElementType().getOpcode(IADD)));
                out.add(this.sumValue.makeStore());
            }
        }

        /*private static class Match extends TerminalOp {
            protected final boolean breakWhenTestReturns;
            protected final boolean defaultReturnValue;

            protected LVTReference predicateValue;

            public Match(MethodInsnNode stageInsn, boolean breakWhenTestReturns, boolean defaultReturnValue) {
                super(stageInsn);
                this.breakWhenTestReturns = breakWhenTestReturns;
                this.defaultReturnValue = defaultReturnValue;
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.predicateValue = rawOperandValues.get(0);
            }

            @Override
            public InsnList finalizeReturnValue(Map<Stage, List<LVTReference>> stageLvtReferences) {
                return BytecodeHelper.makeInsnList(new InsnNode(this.defaultReturnValue ? ICONST_1 : ICONST_0));
            }

            @Override
            public void visitCode(InsnList out, Map<Stage, List<LVTReference>> stageLvtReferences, BranchLabels labels, LVTReference consumedValue) {
                PerElementTypeInfo info = ELEMENT_TYPE_INFO.get(this.consumedElementType());

                out.add(this.predicateValue.makeLoad());
                out.add(consumedValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEINTERFACE, info.elementPredicateType.getInternalName(), "test", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, info.elementType), true));
                out.add(new JumpInsnNode(this.breakWhenTestReturns ? IFEQ : IFNE, labels.skipLabel));
                out.add(new InsnNode(this.defaultReturnValue ? ICONST_0 : ICONST_1)); //return opposite of defaultReturnValue on match
                out.add(new JumpInsnNode(GOTO, labels.returnLabel));
            }
        }*/

        private static class Match extends TerminalOp {
            protected final boolean breakWhenTestReturns;
            protected final boolean defaultReturnValue;

            protected LVTReference predicateValue;
            protected LVTReference returnValue;

            public Match(MethodInsnNode stageInsn, boolean breakWhenTestReturns, boolean defaultReturnValue) {
                super(stageInsn);
                this.breakWhenTestReturns = breakWhenTestReturns;
                this.defaultReturnValue = defaultReturnValue;
            }

            @Override
            public void transformOperands(List<Type> rawOperandTypes, List<LVTReference> rawOperandValues, Function<Type, LVTReference> transformedOperandLvtAlloc, InsnList transformInsns, List<AbstractInsnNode> removeInsns) {
                super.transformOperands(rawOperandTypes, rawOperandValues, transformedOperandLvtAlloc, transformInsns, removeInsns);
                this.predicateValue = rawOperandValues.get(0);

                this.returnValue = transformedOperandLvtAlloc.apply(Type.BOOLEAN_TYPE);
                transformInsns.add(new InsnNode(this.defaultReturnValue ? ICONST_1 : ICONST_0));
                transformInsns.add(this.returnValue.makeStore());
            }

            @Override
            public void finalizeReturnValue(InsnList out) {
                out.add(this.returnValue.makeLoad());
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();

                out.add(this.predicateValue.makeLoad());
                out.add(consumedValue.makeLoad());
                out.add(new MethodInsnNode(INVOKEINTERFACE, info.elementPredicateType.getInternalName(), "test", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, info.elementType), true));
                out.add(new JumpInsnNode(this.breakWhenTestReturns ? IFEQ : IFNE, labels.skipLabel));
                out.add(new InsnNode(this.defaultReturnValue ? ICONST_0 : ICONST_1)); //return opposite of defaultReturnValue on match
                out.add(this.returnValue.makeStore());
                out.add(new JumpInsnNode(GOTO, labels.breakLabel));
            }
        }

        private static class Find extends TerminalOp {
            public final boolean first; //if false, then this represents findAny()

            public Find(MethodInsnNode stageInsn, boolean first) {
                super(stageInsn);
                this.first = first;
            }

            @Override
            public void finalizeReturnValue(InsnList out) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();
                out.add(new MethodInsnNode(INVOKESTATIC, info.elementOptionalType.getInternalName(), "empty", Type.getMethodDescriptor(info.elementOptionalType), false));
            }

            @Override
            public void visitCode(InsnList out, BranchLabels labels, LVTReference consumedValue) {
                PerElementTypeInfo info = this.consumedElementTypeInfo();

                out.add(consumedValue.makeLoad());
                out.add(new MethodInsnNode(INVOKESTATIC, info.elementOptionalType.getInternalName(), "of", Type.getMethodDescriptor(info.elementOptionalType, info.elementType), false));
                out.add(new JumpInsnNode(GOTO, labels.returnLabel));
            }
        }
    }
}
