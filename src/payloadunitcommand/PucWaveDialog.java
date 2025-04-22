package payloadunitcommand;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.TextField.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.editor.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.io.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;
import static mindustry.game.SpawnGroup.never;

public class PucWaveDialog extends BaseDialog{
    Seq<SpawnGroup> groups = new Seq<>();
    private @Nullable SpawnGroup expandedGroup;

    private Table table;
    private int search = -1;
    private @Nullable UnitType filterType;
    private Sort sort = Sort.begin;
    private boolean reverseSort = false;
    private boolean checkedSpawns;
    private WaveGraph graph = new WaveGraph();

    public PucWaveDialog(){
        super("@puc.waves.title");

        shown(() -> {
            checkedSpawns = false;
            setup();
        });
        hidden(() -> {
            PUCMain.copyOnExit = Core.settings.getBool("puc.copyExit");
            if(PUCMain.copyOnExit){
                ui.showInfoFade("@waves.copied");
                Core.app.setClipboardText(maps.writeWaves(groups));
            }
        });

        addCloseButton();

        buttons.button("@waves.edit", Icon.edit, () -> {
            BaseDialog dialog = new BaseDialog("@waves.edit");
            dialog.addCloseButton();
            dialog.setFillParent(false);
            dialog.cont.table(Tex.button, t -> {
                var style = Styles.cleart;
                t.defaults().size(280f, 64f).pad(2f);

                t.button("@waves.copy", Icon.copy, style, () -> {
                    ui.showInfoFade("@waves.copied");
                    Core.app.setClipboardText(maps.writeWaves(groups));
                    dialog.hide();
                }).disabled(b -> groups == null || groups.isEmpty()).marginLeft(12f).row();

                t.button("@waves.load", Icon.download, style, () -> {
                    try{
                        groups = maps.readWaves(Core.app.getClipboardText());
                        buildGroups();
                    }catch(Exception e){
                        Log.err(e);
                        ui.showErrorMessage("@waves.invalid");
                    }
                    dialog.hide();
                }).disabled(Core.app.getClipboardText() == null || !Core.app.getClipboardText().startsWith("[")).marginLeft(12f).row();

                t.button("@clear", Icon.none, style, () -> ui.showConfirm("@confirm", "@settings.clear.confirm", () -> {
                    groups.clear();
                    buildGroups();
                    dialog.hide();
                })).marginLeft(12f).row();

                t.button("@settings.reset", Icon.refresh, style, () -> ui.showConfirm("@confirm", "@settings.clear.confirm", () -> {
                    groups = JsonIO.copy(waves.get());
                    buildGroups();
                    dialog.hide();
                })).marginLeft(12f);
            });

            dialog.show();
        }).size(250f, 64f);

        buttons.button(Core.bundle.get("waves.random"), Icon.refresh, () -> {
            groups.clear();
            groups = Waves.generate(1f / 10f);
            buildGroups();
        }).width(200f);
        Core.scene.find("coreinfo").toFront();
    }

    void setup(){
        try{
            groups = maps.readWaves(Core.app.getClipboardText());
        }catch(Exception e){
            Log.err(e);
            Time.run(0.5f * Time.toSeconds, () -> ui.showInfoFade("@waves.invalid"));
        }

        if(groups == null) groups = new Seq<>();

        cont.clear();
        cont.stack(new Table(Tex.clear, main -> {
            main.table(s -> {
                s.image(Icon.zoom).padRight(8);
                s.field(search < 0 ? "" : (search + 1) + "", TextFieldFilter.digitsOnly, text -> {
                    search = groups.any() ? Strings.parseInt(text, 0) - 1 : -1;
                    buildGroups();
                }).growX().maxTextLength(8).get().setMessageText("@waves.search");
                s.button(Icon.units, Styles.emptyi, () -> showUnits(type -> filterType = type, true)).size(46f).tooltip("@waves.filter")
                .update(b -> b.getStyle().imageUp = filterType != null ? new TextureRegionDrawable(filterType.uiIcon) : Icon.filter);
            }).growX().pad(6f).row();

            main.pane(t -> table = t).grow().padRight(8f).scrollX(false).row();

            main.table(t -> {
                t.button("@add", () -> {
                    showUnits(type -> groups.add(expandedGroup = new SpawnGroup(type)), false);
                    buildGroups();
                }).growX().height(70f);

                t.button(Icon.filter, () -> {
                    BaseDialog dialog = new BaseDialog("@waves.sort");
                    dialog.setFillParent(false);
                    dialog.cont.table(Tex.button, f -> {
                        for(Sort s : Sort.all){
                            f.button("@waves.sort." + s, Styles.flatTogglet, () -> {
                                sort = s;
                                dialog.hide();
                                buildGroups();
                            }).size(150f, 60f).checked(s == sort);
                        }
                    }).row();
                    dialog.cont.check("@waves.sort.reverse", b -> {
                        reverseSort = b;
                        buildGroups();
                    }).padTop(4).checked(reverseSort).padBottom(8f);
                    dialog.addCloseButton();
                    dialog.show();
                }).size(64f, 70f).padLeft(6f);
            }).growX();

        }), new Label(Core.bundle.get("block.empty.name") + " / " + (Core.bundle.get("waves.invalid"))){{
            visible(() -> groups.isEmpty());
            this.touchable = Touchable.disabled;
            setWrap(true);
            setAlignment(Align.center, Align.center);
        }}).width(390f).growY();
        cont.add(graph = new WaveGraph()).grow();

        buildGroups();
    }

    void buildGroups(){
        table.clear();
        table.top();
        table.margin(10f);

        if(groups != null){
            groups.sort(Structs.comps(Structs.comparingFloat(sort.sort), Structs.comparingFloat(sort.secondary)));
            if(reverseSort) groups.reverse();

            for(SpawnGroup group : groups){
                if(group.effect == StatusEffects.none) group.effect = null;
                if((search >= 0 && group.getSpawned(search) <= 0) || (filterType != null && group.type != filterType)) continue;

                table.table(Tex.button, t -> {
                    t.margin(0).defaults().pad(3).padLeft(5f).growX().left();
                    t.button(b -> {
                        b.left();
                        b.image(group.type.uiIcon).size(32f).padRight(3).scaling(Scaling.fit);
                        b.add(group.type.localizedName).ellipsis(true).width(110f).left().color(Pal.accent);

                        b.add().growX();

                        b.label(() -> (group.begin + 1) + "").color(Color.lightGray).minWidth(45f).labelAlign(Align.left).left();

                        b.button(Icon.copySmall, Styles.emptyi, () -> {
                            groups.insert(groups.indexOf(group) + 1, expandedGroup = group.copy());
                            buildGroups();
                        }).pad(-6).size(46f).tooltip("@editor.copy");

                        b.button(group.effect != null ?
                        new TextureRegionDrawable(group.effect.uiIcon) :
                        Icon.logicSmall,
                        Styles.emptyi, () -> showEffects(group)).pad(-6).size(46f).scaling(Scaling.fit).tooltip(group.effect != null ? group.effect.localizedName : "@none");

                        b.button(Icon.unitsSmall, Styles.emptyi, () -> showUnits(type -> group.type = type, false)).pad(-6).size(46f).tooltip("@stat.unittype");
                        b.button(Icon.cancel, Styles.emptyi, () -> {
                            groups.remove(group);
                            if(expandedGroup == group) expandedGroup = null;
                            table.getCell(t).pad(0f);
                            t.remove();
                            buildGroups();
                        }).pad(-6).size(46f).padRight(-12f).tooltip("@waves.remove");
                        b.clicked(KeyCode.mouseMiddle, () -> {
                            groups.insert(groups.indexOf(group) + 1, expandedGroup = group.copy());
                            buildGroups();
                        });
                    }, () -> {
                        expandedGroup = expandedGroup == group ? null : group;
                        buildGroups();
                    }).height(46f).pad(-6f).padBottom(0f).row();

                    if(expandedGroup == group){
                        t.table(spawns -> {
                            spawns.field("" + (group.begin + 1), TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.begin = Strings.parseInt(text) - 1;
                                    updateWaves();
                                }
                            }).width(100f);
                            spawns.add("@waves.to").padLeft(4).padRight(4);
                            spawns.field(group.end == never ? "" : (group.end + 1) + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.end = Strings.parseInt(text) - 1;
                                    updateWaves();
                                }else if(text.isEmpty()){
                                    group.end = never;
                                    updateWaves();
                                }
                            }).width(100f).get().setMessageText("∞");
                        }).row();

                        t.table(p -> {
                            p.add("@waves.every").padRight(4);
                            p.field(group.spacing + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text) && Strings.parseInt(text) > 0){
                                    group.spacing = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(100f);
                            p.add("@waves.waves").padLeft(4);
                        }).row();

                        t.table(a -> {
                            a.field(group.unitAmount + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.unitAmount = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);

                            a.add(" + ");
                            a.field(Strings.fixed(Math.max((Mathf.zero(group.unitScaling) ? 0 : 1f / group.unitScaling), 0), 2), TextFieldFilter.floatsOnly, text -> {
                                if(Strings.canParsePositiveFloat(text)){
                                    group.unitScaling = 1f / Strings.parseFloat(text);
                                    updateWaves();
                                }
                            }).width(80f);
                            a.add("@waves.perspawn").padLeft(4);
                        }).row();

                        t.table(a -> {
                            a.field(group.max + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.max = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);

                            a.add("@waves.max").padLeft(5);
                        }).row();

                        t.table(a -> {
                            a.field((int)group.shields + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.shields = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);

                            a.add(" + ");
                            a.field((int)group.shieldScaling + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.shieldScaling = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);
                            a.add("@waves.shields").padLeft(4);
                        }).row();

                        t.check("@waves.guardian", b -> {
                            group.effect = (b ? StatusEffects.boss : null);
                            buildGroups();
                        }).padTop(4).update(b -> b.setChecked(group.effect == StatusEffects.boss)).padBottom(8f).row();

                        t.table(a -> {
                            a.add("@waves.team").padRight(8);

                            a.button(b -> b.image(Tex.whiteui).size(iconSmall).update(i -> i.setColor(group.team == null ? Color.clear : group.team.color)), Styles.squarei,
                            () -> MapObjectivesDialog.showTeamSelect(true, team -> group.team = team)).size(38f);
                        }).padTop(0).row();

                        t.table(a -> {
                            Label ld = new Label("@loadout");
                            ld.clicked( () -> showPayload(group));
                            a.add(ld).padRight(8);
                            a.button(b -> b.image(Icon.export).size(iconSmall), Styles.clearNonei,
                            () -> showPayload(group)).size(38f);

                            Label rs = new Label("@resources");
                            rs.clicked( () -> showItems(group));
                            a.add(rs).padRight(8);
                            a.button(b -> b.image(Icon.download).size(iconSmall), Styles.clearNonei,
                            () -> showItems(group)).size(38f);
                        }).padTop(0).row();

                        t.table(a -> {
                            a.add("@waves.spawn").padRight(8);

                            a.button("", () -> {
                                if(!checkedSpawns){
                                    //recalculate waves when changed
                                    Vars.spawner.reset();
                                    checkedSpawns = true;
                                }

                                BaseDialog dialog = new BaseDialog("@waves.spawn.select");
                                dialog.cont.pane(p -> {
                                    p.background(Tex.button).margin(10f);
                                    int i = 0;
                                    int cols = 4;
                                    int max = 20;

                                    if(spawner.getSpawns().size >= max){
                                        p.add("[lightgray](first " + max + ")").colspan(cols).padBottom(4).row();
                                    }

                                    for(var spawn : spawner.getSpawns()){
                                        p.button(spawn.x + ", " + spawn.y, Styles.flatTogglet, () -> {
                                            group.spawn = Point2.pack(spawn.x, spawn.y);
                                            dialog.hide();
                                        }).size(110f, 45f).checked(spawn.pos() == group.spawn);

                                        if(++i % cols == 0){
                                            p.row();
                                        }

                                        //only display first 20 spawns, you don't need to see more.
                                        if(i >= 20){
                                            break;
                                        }
                                    }

                                    if(spawner.getSpawns().isEmpty()){
                                        p.add("@waves.spawn.none");
                                    }else{
                                        p.button("@waves.spawn.all", Styles.flatTogglet, () -> {
                                            group.spawn = -1;
                                            dialog.hide();
                                        }).size(110f, 45f).checked(-1 == group.spawn);
                                    }
                                }).grow();
                                dialog.setFillParent(false);
                                dialog.addCloseButton();
                                dialog.show();
                            }).width(160f).height(36f).get().getLabel().setText(() -> group.spawn == -1 ? "@waves.spawn.all" : Point2.x(group.spawn) + ", " + Point2.y(group.spawn));

                        }).padBottom(8f).row();
                    }
                }).width(340f).pad(8);

                table.row();
            }

            if(table.getChildren().isEmpty() && groups.any()){

                table.add("@none.found");
            }
        }else{
            table.add("@editor.default");
        }

        updateWaves();
    }

    void showUnits(Cons<UnitType> cons, boolean reset){
        BaseDialog dialog = new BaseDialog(reset ? "@waves.filter" : "");
        dialog.cont.pane(p -> {
            p.defaults().pad(2).fillX();
            if(reset){
                p.button(t -> {
                    t.left();
                    t.image(Icon.none).size(8 * 4).scaling(Scaling.fit).padRight(2f);
                    t.add("@settings.resetKey");
                }, () -> {
                    cons.get(null);
                    dialog.hide();
                    buildGroups();
                }).margin(12f);
            }
            int i = reset ? 1 : 0;
            for(UnitType type : content.units()){
                if(type.isHidden()) continue;
                p.button(t -> {
                    t.left();
                    t.image(type.uiIcon).size(8 * 4).scaling(Scaling.fit).padRight(2f);
                    t.add(type.localizedName);
                }, () -> {
                    cons.get(type);
                    dialog.hide();
                    buildGroups();
                }).margin(12f);
                if(++i % 3 == 0) p.row();
            }
        }).growX().scrollX(false);
        dialog.addCloseButton();
        dialog.show();
    }

    void showEffects(SpawnGroup group){
        BaseDialog dialog = new BaseDialog("");
        dialog.cont.pane(p -> {
            p.defaults().pad(2).fillX();
            p.button(t -> {
                t.left();
                t.image(Icon.none).size(8 * 4).scaling(Scaling.fit).padRight(2f);
                t.add("@settings.resetKey");
            }, () -> {
                group.effect = null;
                dialog.hide();
                buildGroups();
            }).margin(12f);
            int i = 1;
            for(StatusEffect effect : content.statusEffects()){
                if(effect.isHidden() || effect.reactive) continue;
                p.button(t -> {
                    t.left();
                    t.image(effect.uiIcon).size(8 * 4).scaling(Scaling.fit).padRight(2f);
                    t.add(effect.localizedName);
                }, () -> {
                    group.effect = effect;
                    dialog.hide();
                    buildGroups();
                }).margin(12f);
                if(++i % 3 == 0) p.row();
            }
        }).growX().scrollX(false);
        dialog.addCloseButton();
        dialog.show();
    }

    void showPayload(SpawnGroup group){
        BaseDialog dialog = new BaseDialog("");
        Runnable[] rebuild = {null};
        int size = 60;

        if(group.payloads == null){ group.payloads = new Seq<>();}

        dialog.cont.pane(p -> rebuild[0] = () -> {
            p.clear();
            p.defaults().pad(2).fillX();
            for(UnitType l : group.payloads){
                p.table(Tex.button, s -> {
                    s.table(Tex.underline, e -> {
                        Image img = new Image(Icon.cancel);
                        img.clicked(() -> {
                            group.payloads.remove(l);
                            rebuild[0].run();
                        });
                        e.add(img).size(size / 2f).touchable(Touchable.enabled).scaling(Scaling.bounded).padLeft(2f);
                    }).growX().row();
                    s.table(u -> {
                            Image img = new Image(l.uiIcon);
                            img.clicked(() -> {
                                group.payloads.remove(l);
                                rebuild[0].run();
                            });
                            u.add(img).scaling(Scaling.bounded).size(size * 0.85f).touchable(Touchable.enabled).padRight(0f).left();
                            Label txt = new Label(l.localizedName);
                            txt.clicked(() -> {
                                group.payloads.remove(l);
                                rebuild[0].run();
                            });
                            txt.setWrap(true);
                            txt.fillParent = true;
                            u.add(txt).padLeft(0f).touchable(Touchable.enabled).grow();
                    }).growX();
                    s.row();

                }).pad(1.5f).growX().width(350f).row();

            }


        }).growX().scrollX(false);
        rebuild[0].run();
        dialog.addCloseButton();
        dialog.buttons.button( Icon.add, () -> unitPicker(rebuild[0], group)).size(105, 64f);
        dialog.show();

    }

    void unitPicker(Runnable action, SpawnGroup group){
        BaseDialog dialog = new BaseDialog("@waves.edit");
        //For advance mapmakers and funnies
        dialog.addCloseButton();
        final boolean[] advanced = {false};
        Runnable[] rebuild = {null};
        dialog.buttons.button( Icon.filter, () ->{
            advanced[0] = !advanced[0];
            rebuild[0].run();
        }).size(105, 64f);

        dialog.cont.pane( t -> {
            rebuild[0] = () -> {
                t.clear();
                t.defaults().size(280f, 64f).pad(2f).scrollX(false);
                int c = 0;
                for(UnitType item : content.units()){
                    if(!advanced[0] && (!item.unlockedNow() || item.isHidden() || !item.logicControllable)) continue;
                    if(group.payloads.contains(item))continue;

                    StringBuilder tip = new StringBuilder(item.localizedName);
                    if(item.isModded()) tip.append("\n(").append(item.minfo.mod.meta.displayName).append(")");

                    t.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, iconLarge, () -> {
                        group.payloads.addUnique(item);
                        action.run();
                        dialog.hide();
                    }).size(iconXLarge).scaling(Scaling.bounded).tooltip(tip.toString() );

                    if(++c % 6 == 0) t.row();
                }

            };
        });
        rebuild[0].run();
        dialog.show();
    }

    void showItems(SpawnGroup group){
        BaseDialog dialog = new BaseDialog("");
        int size = 60;
        Runnable[] rebuild = {null};
        boolean[] pick = {false};

        if(group.items == null) group.items = new ItemStack(Items.copper, 0);
        dialog.cont.pane(p -> rebuild[0] = () -> {

            if(group.items.amount <= -1 )group.items.amount = 0;
            if(group.items.amount >= 100000 )group.items.amount = 99999;


            p.clear();
            p.defaults().pad(2).fillX();
            if(pick[0]) {
                p.table(Tex.buttonDisabled,u -> {

                    u.defaults().size(280f, 64f).pad(2f).scrollX(false);
                    int c = 0;
                    for(Item item : content.items()){
                        StringBuilder tip = new StringBuilder(item.localizedName);
                        if(item.isModded()) tip.append("\n(").append(item.minfo.mod.meta.displayName).append(")");

                        u.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, iconLarge, () -> {
                            group.items.item = item;
                            pick[0] = false;
                            rebuild[0].run();
                        }).size(iconXLarge).scaling(Scaling.bounded).tooltip(tip.toString()).checked(item == group.items.item);

                        if(++c % 6 == 0) u.row();
                    }
                }).center();
            }else {
                p.table(Tex.button, u -> {
                    Image img = new Image(group.items.item.uiIcon);
                    img.clicked(() -> {
                        pick[0] = true;
                        rebuild[0].run();
                    });
                    u.add(img).scaling(Scaling.bounded).size(size * 0.85f).touchable(Touchable.enabled).padRight(0f).left().get();

                    u.table( i -> {
                        i.table( k -> {
                            Label txt = new Label(group.items.item.localizedName);
                            txt.clicked(() -> {
                                pick[0] = true;
                                rebuild[0].run();
                            });
                            txt.setWrap(true);
                            txt.fillParent = true;
                            k.add(txt).touchable(Touchable.enabled).growX().center().padBottom(0f);
                        }).growX();

                        i.row();

                        i.table( k -> {
                            Label pb = new Label("+");
                            pb.clicked(() -> {
                                group.items.amount++;
                                rebuild[0].run();
                            });
                            k.add(pb).padLeft(0f).touchable(Touchable.enabled).grow().fontScale(2f);

                            k.add(group.items.amount + "").center().grow();

                            Label mb = new Label("-");
                            mb.clicked(() -> {
                                group.items.amount--;
                                rebuild[0].run();
                            });
                            k.add(mb).padLeft(0f).padRight(5f).touchable(Touchable.enabled).fontScale(2f);

                            Label pc = new Label(Iconc.pencil + "");
                            pc.clicked(() -> ui.showTextInput("@configure", group.items.item.localizedName, 10, group.items.amount + "", true, str -> {
                                if(Strings.canParsePositiveInt(str)){
                                    group.items.amount = Strings.parseInt(str);
                                    rebuild[0].run();
                                }
                            }));
                            k.add(pc).touchable(Touchable.enabled);
                        }).growX();
                    }).growX();


                }).center().size(300, 80f).growY();
            }

        }).growX().scrollX(false);
        rebuild[0].run();
        dialog.addCloseButton();
        dialog.show();
    }


    enum Sort{
        begin(g -> g.begin, g -> g.type.id),
        health(g -> g.type.health),
        type(g -> g.type.id);

        static final Sort[] all = values();

        final Floatf<SpawnGroup> sort, secondary;

        Sort(Floatf<SpawnGroup> sort){
            this(sort, g -> g.begin);
        }

        Sort(Floatf<SpawnGroup> sort, Floatf<SpawnGroup> secondary){
            this.sort = sort;
            this.secondary = secondary;
        }
    }

    void updateWaves(){
        graph.groups = groups;
        graph.rebuild();
    }
}
