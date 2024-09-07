package payloadunitcommand;

import arc.Events;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.EventType;
import mindustry.gen.*;
import mindustry.mod.Mod;
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

            netClient.addPacketHandler("wuc-true", s -> validHost = true);
        });

        Events.on(EventType.WorldLoadEvent.class, a ->{
            validHost = false;
            carryBlocks = false;
            if(net.client()) Call.serverPacketReliable("wuc-check", "");
            if(!headless) Time.run(0.5f * Time.toSeconds, this::rebuildSubButtons);
        });

    }

    public void globalPackets(){
        netServer.addPacketHandler("wuc-check", (p, s) -> Call.clientPacketReliable(p.con(), "wuc-true", ""));
        netServer.addPacketHandler("wuc-take", (p, s) -> {
            String[] params = s.split(" ");
            String[] ids = params[2].split("-");
            Seq<Unit> list = new Seq<>();
            for (String id : ids) {
                Groups.unit.getByID(Integer.parseInt(id));
            }
            payloadHandler(params[0].equals("true"), params[1].equals("true"), list);
        });
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

        if(net.client() && !validHost)return;
        float pad = 1f;
        pucTable.table(Tex.pane, t ->{
            t.margin(0f);
            t.button(Icon.distribution, Styles.clearNoneTogglei, () -> carryBlocks = !carryBlocks).update(l -> l.setChecked(carryBlocks)).name("puc-take-block").pad(pad).size(48f).row();
            t.button(Icon.up, Styles.clearNonei, () -> buttonHandler(true)).name("puc-take-button").pad(pad).size(42f).row();
            t.button(Icon.down, Styles.clearNonei, () -> buttonHandler(false)).name("puc-drop-button").pad(pad).size(48f).row();
        });
        pucTable.add(new Element()).width((337)).height(40).margin(12f).touchable( Touchable.disabled);
    }

    public void buttonHandler(boolean take) {
        Seq<Unit> list = control.input.selectedUnits.copy();
        list.removeAll(u -> !(u instanceof Payloadc));

        if (Vars.net.server() || !Vars.net.active()) {
            payloadHandler(take, carryBlocks, list);
        } if(net.client() && validHost){
            StringBuilder ids = new StringBuilder();
            for(int i = 0; i < list.size; i++) ids.append(list.get(i).id).append("-");
            Call.serverPacketReliable("wuc-take", Strings.format("@ @ @", take, carryBlocks, ids.toString()));
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



}

