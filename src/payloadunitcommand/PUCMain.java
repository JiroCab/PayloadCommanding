package payloadunitcommand;

import arc.*;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.core.*;
import mindustry.entities.Units;
import mindustry.game.EventType;
import mindustry.gen.*;
import mindustry.mod.Mod;
import mindustry.net.Administration.*;
import mindustry.ui.Styles;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.*;

public class PUCMain extends Mod {
    public Table pucTable = new Table();
    public Seq<Unit> targeted = new Seq<>();
    public boolean validHost = false, carryBlocks;

    public PUCMain(){

        Events.on(EventType.ServerLoadEvent.class, e -> globalPackets());

        Events.on(EventType.ClientLoadEvent.class, a -> {
            globalPackets();
            buildButton();

            netClient.addPacketHandler("puc-true", s -> validHost = true);
            netClient.addPacketHandler("puc-enter", s -> takeHandler(s));
            ui.settings.addCategory("@puc-settings", Icon.download, table -> {
                table.checkPref("puc-enter-button", Version.number <= 8);
                table.checkPref("puc-block-button", true);
            });

        });

        Events.on(EventType.WorldLoadEvent.class, a ->{
            validHost = false;
            carryBlocks = false;
            if(net.client()) Call.serverPacketReliable("puc-check", "");
            if(!headless) Time.run(0.5f * Time.toSeconds, this::rebuildSubButtons);
        });

    }

    public void globalPackets(){
        Config limit = new Config("puc-RateLimit", "whether or now payload commanding is limited by Block interaction rate limit", true);
        netServer.addPacketHandler("puc-check", (p, s) -> Call.clientPacketReliable(p.con(), "puc-true", ""));
        netServer.addPacketHandler("puc-take", (p, s) -> {
            if(!limit.bool()){
                takePacket(s, p);
                return;
            }

            Ratekeeper rate = p.getInfo().rate;
            if(rate.allow(Config.interactRateWindow.num() * 1000L, Config.interactRateLimit.num())) takePacket(s, p);
            else{
                if(rate.occurences > Config.interactRateKick.num()) p.kick("You are interacting with payload commands too quickly. (Puc", 1000 * 30);
                else if(p.getInfo().messageTimer.get(60f * 2f)) p.sendMessage("[scarlet]You are interacting with payload commands too quickly.");
            }

        });

        netServer.addPacketHandler("puc-enter-req", (p, s) -> {
            if(!limit.bool()){
                sendTakeActual(s, p);
                return;
            }

            Ratekeeper rate = p.getInfo().rate;
            if(rate.allow(Config.interactRateWindow.num() * 1000L, Config.interactRateLimit.num())) sendTakeActual(s, p);
            else{
                if(rate.occurences > Config.interactRateKick.num()) p.kick("You are interacting with payload commands too quickly. (Puc", 1000 * 30);
                else if(p.getInfo().messageTimer.get(60f * 2f)) p.sendMessage("[scarlet]You are interacting with payload commands too quickly.");
            }

        });
    }

    public void takePacket(String s, @Nullable  Player p){
        if(p != null){
            try{
                netServer.writeEntitySnapshot(p);
                netServer.writeBlockSnapshots();
            } catch(Exception e){
                e.printStackTrace();
            }
        }

        String[] params = s.split(" ");
        String[] ids = params[2].split("-");
        Seq<Unit> list = new Seq<>();
        for (String id : ids) {
            list.add(Groups.unit.getByID(Integer.parseInt(id)));
        }
        payloadHandler(params[0].equals("true"), params[1].equals("true"), list);
    }

    public void buildButton (){
        Vars.ui.hudGroup.fill( cont ->{
            cont.name = "payload-unit-command-cont";
            cont.visible(() -> ui.hudfrag.shown && control.input.commandMode);
            cont.bottom().right().bottom().toBack();
            rebuildSubButtons();
            cont.table(tab -> tab.add(pucTable)).row();
        });
    }

    public void rebuildSubButtons(){
        pucTable.reset();
        pucTable.clear();
        float scl = Scl.scl(1f);

        if(net.client() && !validHost)return;
        float pad = 1f;
        pucTable.table(Tex.pane, t ->{
            t.margin(0f);
            //V7 back port
            if(Core.settings.getBool("puc-enter-button"))t.button(Icon.download, Styles.clearNonei, this::enterButton).name("puc-enter-button").tooltip(Core.bundle.get("PUC.entertooltip")).wrap().pad(pad).size(42f * scl).row();
            if(Core.settings.getBool("puc-block-button"))t.button(Icon.distribution, Styles.clearNoneTogglei, () -> carryBlocks = !carryBlocks).update(l -> l.setChecked(carryBlocks)).name("puc-take-block").pad(pad).size(48f * scl).row();
            t.button(Icon.up, Styles.clearNonei, () -> buttonHandler(true)).name("puc-take-button").pad(pad).size(42f * scl).row();
            t.button(Icon.down, Styles.clearNonei, () -> buttonHandler(false)).name("puc-drop-button").pad(pad).size(48f * scl).row();
        });
        pucTable.add(new Element()).width((337)).height(40).margin(12f * scl).touchable( Touchable.disabled);
    }

    public void buttonHandler(boolean take) {
        if(net.client() && !validHost) return;
        Seq<Unit> list = control.input.selectedUnits.copy();
        list.removeAll(u -> !(u instanceof Payloadc));
        if(list.size < 1) return;

        if (Vars.net.server() || !Vars.net.active()) payloadHandler(take, carryBlocks, list);
        if(net.client() || net.server()){
            StringBuilder ids = new StringBuilder();
            for(int i = 0; i < list.size; i++) ids.append(list.get(i).id).append("-");
            Call.serverPacketReliable("puc-take", Strings.format("@ @ @", take, carryBlocks, ids.toString()));
        }
    }

    public void payloadHandler(boolean take, boolean blocks, Seq<Unit> seq) {
        if (seq.size == 0) return;
        targeted.clear();
        for (Unit unit : seq) {
            if (!(unit instanceof Payloadc p)) continue;

            if (take) {
                Unit close = Units.closest(unit.team(), unit.x(), unit.y(), unit.type.hitSize * 2f, u -> u.isAI() && u.isGrounded() && p.canPickup(u) && u.within(unit, u.hitSize + unit.hitSize));
                if (close != null && !targeted.contains(close)) { //This is to prevent quantum entanglement when multiple units try to take the same target
                    targeted.add(close);
                    Call.pickedUnitPayload(unit, close);
                } else if(blocks){
                    Building build = world.buildWorld(unit.x, unit.y);
                    Payloadc pay = (Payloadc) unit;

                    if(build != null && build.team == unit.team){
                        Payload current = build.getPayload();
                        if(current != null && pay.canPickupPayload(current)){
                            Call.pickedBuildPayload(unit, build, false);
                            //pick up whole building directly
                        }else if(build.block.buildVisibility != BuildVisibility.hidden && build.canPickup() && pay.canPickup(build)){
                            Call.pickedBuildPayload(unit, build, true);
                        }
                    }
                }
            } else if (p.hasPayload()) {
                Call.payloadDropped(unit, unit.x, unit.y);
            }
        }
    }


    public void  enterButton(){
        if(net.client() && !validHost)return;
        Seq<Unit> list = control.input.selectedUnits.copy();
        list.removeAll(u -> (u.buildOn() == null || !u.buildOn().canControlSelect(u)));
        if(list.size < 1) return;


        if(net.client()){
            sendTakeRequest(list);

        } else if(!net.active() || Vars.net.server()) {
            if(Vars.net.server()){
                StringBuilder ids = new StringBuilder();
                for(int i = 0; i < list.size; i++) ids.append(list.get(i).id).append("-");
                sendTakeActual(ids.toString(), null);
            }
            payloadEnter(list);
        }
    }

    public void sendTakeRequest(Seq<Unit> list){
        StringBuilder ids = new StringBuilder();
        for(int i = 0; i < list.size; i++) ids.append(list.get(i).id).append("-");
        Call.serverPacketReliable("puc-enter-req", ids.toString());
    }

    public void sendTakeActual(String s, @Nullable Player p){
        if (Vars.net.server() || !Vars.net.active()) takeHandler(s);

        if (Vars.net.server() || Vars.net.client()) {
            Call.clientPacketReliable("puc-enter", s);
        }
    }

    public void takeHandler(String s){
        String[] ids = s.split("-");
        Seq<Unit> list = new Seq<>();
        for (String id : ids) {
            Unit unt = Groups.unit.getByID(Integer.parseInt(id));
            if(unt != null)list.add(unt);
        }
        payloadEnter(list);
    }


    public void payloadEnter(Seq<Unit> seq){
        for (Unit unit : seq) {
            Building b = unit.buildOn();
            if(b != null && b.canControlSelect(unit)){
                b.onControlSelect(unit);
            }
        }
    }

}

