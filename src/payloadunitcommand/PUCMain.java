package payloadunitcommand;

import arc.*;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ai.*;
import mindustry.game.EventType;
import mindustry.gen.*;
import mindustry.mod.Mod;
import mindustry.ui.Styles;

import static mindustry.Vars.*;

public class PUCMain extends Mod {
    public Table pucTable = new Table();
    public static PucWaveDialog waveDialog;
    public static boolean validHost = false, copyOnExit = true;

    public PUCMain(){

        Events.on(EventType.ClientLoadEvent.class, a -> {
            buildButton();
            ui.menufrag.addButton("@puc-waves", Icon.pencil, () -> waveDialog.show());
        });

    }

    @Override
    public void init(){
        waveDialog = new PucWaveDialog();
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
            //No point in having enter payload in v8
            t.button(Icon.upload, Styles.clearNonei, () -> buttonHandler(UnitCommand.loadUnitsCommand)).name("puc-unit-load-button").pad(pad).size(42f * scl).row();
            t.button(Icon.up, Styles.clearNonei, () -> buttonHandler(UnitCommand.loadBlocksCommand)).name("puc-block-load-button").pad(pad).size(42f * scl).row();
            t.button(Icon.download, Styles.clearNonei, () -> buttonHandler(UnitCommand.unloadPayloadCommand)).name("puc-drop-button").pad(pad).size(48f * scl).row();
        });
        pucTable.add(new Element()).width((337)).height(40).margin(12f * scl).touchable( Touchable.disabled);
    }


    public void buttonHandler(UnitCommand cmd) {
        Seq<Unit> list = control.input.selectedUnits.copy();
        list.retainAll(u -> u instanceof Payloadc p);
        if(list.size < 1) return;

        Call.setUnitCommand(player, list.mapInt(un -> un.id).toArray(), cmd);

    }
}

