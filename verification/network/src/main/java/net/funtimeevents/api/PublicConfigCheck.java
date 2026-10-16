package net.funtimeevents.api;
import net.funtimeevents.internal.game.*;
import net.funtimeevents.internal.tracker.TrackerRuntime;
import net.funtimeevents.spi.OfflineSender;

/** Pure config/disabled-runtime test; it must not read Minecraft state. */
public final class PublicConfigCheck {
    public static void main(String[] args) {
        var d=new FteConfig(new FteConfig.Builder());
        require(d.tickIntervalSeconds()==10&&d.wsMode()&&d.compression()&&!d.offlineMode(),"defaults");
        require(d.tabPlayersEnabled()&&d.bansEnabled()&&d.dungeonEnabled()&&d.hellMapEnabled()&&d.mineEnabled()&&d.coordinatesEnabled(),"enabled defaults");
        require(!new FteConfig(new FteConfig.Builder().disableScanTabPlayers()).tabPlayersEnabled(),"TAB flag");
        require(!new FteConfig(new FteConfig.Builder().disableBansTracker()).bansEnabled(),"Ban flag");
        require(!new FteConfig(new FteConfig.Builder().disableScanDungeon()).dungeonEnabled(),"Dungeon flag");
        require(!new FteConfig(new FteConfig.Builder().disableScanHellMap()).hellMapEnabled(),"Hell flag");
        require(!new FteConfig(new FteConfig.Builder().disableScanMine()).mineEnabled(),"Mine flag");
        require(!new FteConfig(new FteConfig.Builder().disableEventCoordinatesTracker()).coordinatesEnabled(),"Coordinates flag");
        require(new FteConfig(new FteConfig.Builder().tickIntervalSeconds(0)).tickIntervalSeconds()==1,"minimum interval");
        ServerAccess access=new ServerAccess(){public String serverAddress(){return "test.funtime.su";}public String sidebarTitle(){return "Анархия-401";}};
        var r=new TrackerRuntime(access,OfflineSender.INSTANCE,20,false,false,false,false,false,false);
        r.joined();for(int i=0;i<200;i++)r.tick();
        r.message(new IncomingMessage(){
            public String rawText(){throw new AssertionError("Disabled tracker read message");}
            public String plainText(){throw new AssertionError("Disabled tracker read text");}
            public String hoverText(){throw new AssertionError("Disabled tracker read hover");}
        });
        require(r.periodic()==10&&r.bans().state().serverId()==401,"interval/context while disabled");
        r.disconnected();require(!r.bans().isJoined()&&r.bans().state().serverId()==-1,"disabled reset");
        System.out.println("[PHASE4A] CONFIG_PASS defaults flags minimum custom20tick disabled_runtime_no_game_reads");
    }
    private static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
