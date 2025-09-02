package net.daporkchop.ppatches.modules.vanilla.reduceNetworkThreadCount;

import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraftforge.common.config.Config;

/**
 * @author DaPorkchop_
 */
public class ModuleConfigReduceNetworkThreadCount extends PPatchesConfig.ModuleConfigBase {
    @Config.Comment({
            "If true, the integrated server will use the client local event loop instead of the server's for processing the local player.",
            "If false, the integrated server will process the local player on the server network event loop, which is vanilla behavior.",
            "Enabling this will reduce the total number of running threads in singleplayer worlds by one. In general, this should actually reduce latency and slightly"
                    + " increase performance in singleplayer, but could actually slow things down if for some reason the network thread is overloaded (most likely if"
                    + " a mod is doing very expensive work in a packet handler).",
    })
    @Config.RequiresWorldRestart
    public boolean useClientLocalEventLoopOnIntegratedServer = true;

    @Config.Comment({
            "If true, the integrated server will use the client network event loop instead of the server's for processing LAN players.",
            "If useClientLocalEventLoopOnIntegratedServer is false, the the local player will also be processed on the client network event loop.",
            "Enabling this will prevent the server network event loop's threads from being started by singleplayer LAN worlds.",
    })
    @Config.RequiresWorldRestart
    public boolean useClientNetworkEventLoopOnIntegratedServer = true;

    @Config.Comment({
            "The maximum number of threads in the client network event loop. Set this value to zero for vanilla behavior.",
            "A value of 1 should be more than sufficient for nearly all use cases. This should only need to be increased if you're hosting a LAN world with a huge"
                    + " number of players and useClientNetworkEventLoopOnIntegratedServer is enabled.",
    })
    @Config.RangeInt(min = 0)
    @Config.RequiresMcRestart
    public int clientNetworkEventLoopSize = 1;

    @Config.Comment({
            "The maximum number of threads in the server network event loop. Set this value to zero for vanilla behavior.",
            "A low value of 1 or 2 should be more than sufficient for pretty much all dedicated servers and will slightly increase overall system performance, but the default"
                    + " setting preserves the original vanilla behavior because you should test this option out before enabling it on your server.",
    })
    @Config.RangeInt(min = 0)
    @Config.RequiresMcRestart
    public int serverNetworkEventLoopSize = 0;

    @Config.Comment({
            "The maximum number of threads to use for pinging servers in the server list.",
            "Vanilla uses an unlimited number of threads by default, which is generally overkill since the only thing they do is send a single DNS query.",
    })
    @Config.RangeInt(min = 1)
    @Config.RequiresMcRestart
    public int serverPingThreadCount = 1;

    @Config.Comment({
            "The number of seconds which the server ping threads may be idle for before stopping.",
            "A negative value means that the server ping thread will never stop once started."
    })
    @Config.RequiresMcRestart
    public int serverPingThreadKeepAliveTime = 5;

    public ModuleConfigReduceNetworkThreadCount(PPatchesConfig.ModuleState defaultState) {
        super(defaultState);
    }
}
