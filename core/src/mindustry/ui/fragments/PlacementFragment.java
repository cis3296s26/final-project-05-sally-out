package mindustry.ui.fragments;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.math.*;

import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.ai.*;

import static mindustry.Vars.*;



public class PlacementFragment{
    final int rowWidth = 4;

    public Category currentCategory = Category.distribution;
    Seq<Block> returnArray = new Seq<>(), returnArray2 = new Seq<>();
    Seq<Category> returnCatArray = new Seq<>();
    boolean[] categoryEmpty = new boolean[Category.all.length];
    ObjectMap<Category,Block> selectedBlocks = new ObjectMap<>();
    ObjectFloatMap<Category> scrollPositions = new ObjectFloatMap<>();
    @Nullable Block menuHoverBlock;
    @Nullable Displayable hover;
    @Nullable Building lastFlowBuild, nextFlowBuild;
    @Nullable Object lastDisplayState;
    @Nullable Team lastTeam;
    boolean wasHovered;
    Table blockTable, toggler, topTable, blockCatTable, commandTable, unitTable;
    Stack mainStack;
    ScrollPane blockPane;
    Runnable rebuildCommand;
    boolean wasCommandMode, wasUnitMode;

    public PlacementFragment(){
        Events.on(WorldLoadEvent.class, event -> {
            Core.app.post(() -> {
                currentCategory = Category.distribution;
                control.input.block = null;
                rebuild();
            });
        });

        Events.run(Trigger.unitCommandChange, () -> {
            if(rebuildCommand != null) rebuildCommand.run();
        });

        Events.on(UnlockEvent.class, event -> {
            if(event.content instanceof Block) rebuild();
        });

        Events.on(ResetEvent.class, event -> selectedBlocks.clear());

        Events.run(Trigger.update, () -> {
            if(lastFlowBuild != null && lastFlowBuild != nextFlowBuild){
                if(lastFlowBuild.flowItems() != null) lastFlowBuild.flowItems().stopFlow();
                if(lastFlowBuild.liquids != null) lastFlowBuild.liquids.stopFlow();
            }
            lastFlowBuild = nextFlowBuild;
            if(nextFlowBuild != null){
                if(nextFlowBuild.flowItems() != null) nextFlowBuild.flowItems().updateFlow();
                if(nextFlowBuild.liquids != null) nextFlowBuild.liquids.updateFlow();
            }
        });
    }

    public Displayable hover(){ return hover; }

    void rebuild(){
        Group group = toggler.parent;
        int index = toggler.getZIndex();
        toggler.remove();
        build(group);
        toggler.setZIndex(index);
    }

    boolean gridUpdate(InputHandler input){
        scrollPositions.put(currentCategory, blockPane.getScrollY());

        if(Core.input.keyTap(Binding.pick) && player.isBuilder() && !Core.scene.hasDialog()){
            var build = world.buildWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);
            if(build != null && build.inFogTo(player.team())) build = null;

            Block tryRecipe = build == null ? null : build instanceof ConstructBuild c ? c.current : build.block;
            Object tryConfig = build == null || !build.block.copyConfig ? null : build.config();
            for(BuildPlan req : player.unit().plans()){
                if(!req.breaking && req.block.bounds(req.x, req.y, Tmp.r1).contains(Core.input.mouseWorld())){
                    tryRecipe = req.block;
                    tryConfig = req.config;
                    break;
                }
            }
            if(tryRecipe != null && tryRecipe.isVisible() && unlocked(tryRecipe)){
                input.block = tryRecipe;
                tryRecipe.lastConfig = tryConfig;
                currentCategory = input.block.category;
                return true;
            }
        }

        if(ui.chatfrag.shown() || ui.consolefrag.shown() || Core.scene.hasKeyboard()) return false;

        // Category switching
        if(Core.input.keyTap(Binding.category_prev)){
            do currentCategory = currentCategory.prev();
            while(categoryEmpty[currentCategory.ordinal()]);
            input.block = getSelectedBlock(currentCategory);
            return true;
        }
        if(Core.input.keyTap(Binding.category_next)){
            do currentCategory = currentCategory.next();
            while(categoryEmpty[currentCategory.ordinal()]);
            input.block = getSelectedBlock(currentCategory);
            return true;
        }

        if(Core.input.keyTap(Binding.block_info)){
            var build = world.buildWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);
            Block hovering = build == null ? null : build instanceof ConstructBuild c ? c.current : build.block;
            Block displayBlock = menuHoverBlock != null ? menuHoverBlock : input.block != null ? input.block : hovering;
            if(displayBlock != null && displayBlock.unlockedNow()){
                ui.content.show(displayBlock);
                Events.fire(new BlockInfoEvent());
            }
        }
        return false;
    }

    public void build(Group parent){
        parent.fill(full -> {
            toggler = full;
            full.bottom().right().visible(() -> ui.hudfrag.shown);

            full.table(frame -> {
                Runnable rebuildCategory = () -> {
                    blockTable.clear();
                    blockTable.top().margin(5);
                    int index = 0;

                    ButtonGroup group = new ButtonGroup();
                    group.setMinCheckCount(0);

                    for(Block block : getUnlockedByCategory(currentCategory)){
                        if(!unlocked(block)) continue;
                        if(index++ % rowWidth == 0) blockTable.row();

ImageButton button = (ImageButton) blockTable.button(
    new TextureRegionDrawable(block.uiIcon),
    Styles.selecti,
    () -> {
        if(unlocked(block)){
            if((Core.input.keyDown(KeyCode.shiftLeft) || Core.input.keyDown(KeyCode.controlLeft)) && Fonts.getUnicode(block.name) != 0){
                Core.app.setClipboardText((char)Fonts.getUnicode(block.name) + "");
                ui.showInfoFade("@copied");
            }else{
                control.input.block = control.input.block == block ? null : block;
                selectedBlocks.put(currentCategory, control.input.block);
            }
        }
    }
).size(46f).group(group).name("block-" + block.name).get();
                        button.resizeImage(iconMed);

                        button.update(() -> {
                            Building core = player.core();
                            Color color = (state.rules.infiniteResources || (core != null && (core.items.has(block.requirements, state.rules.buildCostMultiplier) || state.rules.infiniteResources))) && player.isBuilder() ? Color.white : Color.gray;
                            button.forEach(elem -> elem.setColor(color));
                            button.setChecked(control.input.block == block);
                            if(!block.isPlaceable()) button.forEach(elem -> elem.setColor(Color.darkGray));
                        });
                        button.hovered(() -> menuHoverBlock = block);
                        button.exited(() -> { if(menuHoverBlock == block) menuHoverBlock = null; });
                    }
                    if(index < 4){
                        for(int i = 0; i < 4-index; i++) blockTable.add().size(46f);
                    }
                    blockTable.act(0f);
                    blockPane.setScrollYForce(scrollPositions.get(currentCategory, 0));
                    Core.app.post(() -> {
                        blockPane.setScrollYForce(scrollPositions.get(currentCategory, 0));
                        blockPane.act(0f);
                        blockPane.layout();
                    });
                };

                // top info box
                frame.table(Tex.buttonEdge2, top -> {
                    topTable = top;
                    top.add(new Table()).growX().update(topTable -> {
                        Displayable hovered = hover;
                        Block displayBlock = menuHoverBlock != null ? menuHoverBlock : control.input.block;
                        Object displayState = displayBlock != null ? displayBlock : hovered;
                        boolean isHovered = displayBlock == null;
                        if(wasHovered == isHovered && lastDisplayState == displayState && lastTeam == player.team()) return;

                        topTable.clear();
                        topTable.top().left().margin(5);
                        lastDisplayState = displayState;
                        wasHovered = isHovered;
                        lastTeam = player.team();

                        if(displayBlock != null){
                            // block info (unchanged)
                        } else if(control.input.unitType != null && control.input.unitPlacementMode && !control.input.commandMode){
                            UnitType unit = control.input.unitType;
                            topTable.table(header -> {
                                header.left();
                                header.add(new Image(unit.uiIcon)).size(8 * 4);
                                header.labelWrap(unit.localizedName).left().width(190f).padLeft(5);
                                header.add().growX();
                            }).growX().left();
                            topTable.row();
                            topTable.add("@free").color(Color.lightGray).left().padTop(2);
                            if(!unit.unlockedNow()){
                                topTable.row();
                                topTable.table(b -> {
                                    b.image(Icon.cancel).padRight(2).color(Color.scarlet);
                                    b.add("@unit.unavailable").width(190f).wrap();
                                    b.left();
                                }).padTop(2).left();
                            }
                        } else if(hovered != null){
                            hovered.display(topTable);
                        }
                    });
                }).colspan(3).fillX().visible(this::hasInfoBox).touchable(Touchable.enabled).row();

                frame.image().color(Pal.gray).colspan(3).height(4).growX().row();

                blockCatTable = new Table();
                commandTable = new Table(Tex.pane2);
                unitTable = new Table(Tex.pane2);
                buildUnitTable(unitTable);

                mainStack = new Stack();
                mainStack.addChild(blockCatTable);
                mainStack.addChild(commandTable);
                mainStack.addChild(unitTable);

                // visibility control
                Runnable updateStack = () -> {
                    blockCatTable.visible = !control.input.commandMode && !control.input.unitPlacementMode;
                    commandTable.visible = control.input.commandMode;
                    unitTable.visible = control.input.unitPlacementMode && !control.input.commandMode;
                };

                // initialize
                updateStack.run();
                // keep synced in the frame update
                frame.update(() -> {
                    if(gridUpdate(control.input)) rebuildCategory.run();
                    updateStack.run();
                });

                frame.add(mainStack).colspan(3).fill();
                frame.row();

                frame.rect((x, y, w, h) -> {
                    if(Core.scene.marginBottom > 0) Tex.paneLeft.draw(x, 0, w, y);
                }).colspan(3).fillX().row();

                // commandTable: commanded units (unchanged, but now UnitCommand is recognised)
                {
                    commandTable.touchable = Touchable.enabled;
                    commandTable.add(Core.bundle.get("commandmode.name")).fill().center().labelAlign(Align.center).row();
                    commandTable.image().color(Pal.accent).growX().pad(20f).padTop(0f).padBottom(4f).row();
                    commandTable.table(u -> {
                        u.left();
                        int[] curCount = {0};
                        UnitCommand[] currentCommand = {null};
                        Seq<UnitCommand> commands = new Seq<>();

                        rebuildCommand = () -> {
                            u.clearChildren();
                            var units = control.input.selectedUnits;
                            if(units.size > 0){
                                int[] counts = new int[content.units().size];
                                for(var unit : units) counts[unit.type.id]++;
                                commands.clear();
                                boolean firstCommand = false;
                                Table unitlist = u.table().growX().left().get();
                                unitlist.left();

                                int col = 0;
                                for(int i = 0; i < counts.length; i++){
                                    if(counts[i] > 0){
                                        var type = content.unit(i);
                                        unitlist.add(new ItemImage(type.uiIcon, counts[i])).tooltip(type.localizedName).pad(4).with(b -> {
                                            var listener = new ClickListener();
                                            b.clicked(KeyCode.mouseLeft, () -> {
                                                control.input.selectedUnits.removeAll(unit -> unit.type != type);
                                                Events.fire(Trigger.unitCommandChange);
                                            });
                                            b.clicked(KeyCode.mouseRight, () -> {
                                                control.input.selectedUnits.removeAll(unit -> unit.type == type);
                                                Events.fire(Trigger.unitCommandChange);
                                            });
                                            b.addListener(listener);
                                            b.addListener(new HandCursorListener());
                                            b.update(() -> ((Group)b.getChildren().first()).getChildren().first().setColor(listener.isOver() ? Color.lightGray : Color.white));
                                        });
                                        if(++col % 7 == 0) unitlist.row();
                                        if(!firstCommand){
                                            commands.add(type.commands);
                                            firstCommand = true;
                                        }else{
                                            commands.removeAll(com -> !Structs.contains(type.commands, com));
                                        }
                                    }
                                }
                                if(commands.size > 1){
                                    u.row();
                                    u.table(coms -> {
                                        for(var command : commands){
                                            coms.button(Icon.icons.get(command.icon, Icon.cancel), Styles.clearNoneTogglei, () -> {
                                                IntSeq ids = new IntSeq();
                                                for(var unit : units) ids.add(unit.id);
                                                Call.setUnitCommand(Vars.player, ids.toArray(), command);
                                            }).checked(i -> currentCommand[0] == command).size(50f).tooltip(command.localized());
                                        }
                                    }).fillX().padTop(4f).left();
                                }
                            }else{
                                u.add(Core.bundle.get("commandmode.nounits")).color(Color.lightGray).growX().center().labelAlign(Align.center).pad(6);
                            }
                        };

                        u.update(() -> {
                            boolean hadCommand = false;
                            UnitCommand shareCommand = null;
                            for(var unit : control.input.selectedUnits){
                                if(unit.isCommandable()){
                                    var nextCommand = unit.command().command;
                                    if(hadCommand){
                                        if(shareCommand != nextCommand) shareCommand = null;
                                    }else{
                                        shareCommand = nextCommand;
                                        hadCommand = true;
                                    }
                                }
                            }
                            currentCommand[0] = shareCommand;
                            int size = control.input.selectedUnits.size;
                            if(curCount[0] != size){
                                curCount[0] = size;
                                rebuildCommand.run();
                            }
                        });
                        rebuildCommand.run();
                    }).grow();
                }

                // blockCatTable
                {
                    blockCatTable.table(Tex.pane2, blocksSelect -> {
                        blocksSelect.margin(4).marginTop(0);
                        blockPane = blocksSelect.pane(blocks -> blockTable = blocks).height(194f).update(pane -> {
                            if(pane.hasScroll()){
                                Element result = Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true);
                                if(result == null || !result.isDescendantOf(pane)) Core.scene.setScrollFocus(null);
                            }
                        }).grow().get();
                        blockPane.setStyle(Styles.smallPane);
                        blocksSelect.row();
                        blocksSelect.table(control.input::buildPlacementUI).name("inputTable").growX();
                    }).fillY().bottom().touchable(Touchable.enabled);

                    blockCatTable.table(categories -> {
                        categories.bottom();
                        categories.add(new Image(Styles.black6){
                            @Override public void draw(){
                                if(height <= Scl.scl(3f)) return;
                                getDrawable().draw(x, y, width, height - Scl.scl(3f));
                            }
                        }).colspan(2).growX().growY().padTop(-3f).row();
                        categories.defaults().size(50f);

                        ButtonGroup group = new ButtonGroup();
                        for(Category cat : Category.all){
                            Seq<Block> blocks = getUnlockedByCategory(cat);
                            categoryEmpty[cat.ordinal()] = blocks.isEmpty();
                        }
                        boolean needsAssign = categoryEmpty[currentCategory.ordinal()];
                        int f = 0;
                        for(Category cat : getCategories()){
                            if(f++ % 2 == 0) categories.row();
                            if(categoryEmpty[cat.ordinal()]){
                                categories.image(Styles.black6);
                                continue;
                            }
                            if(needsAssign){
                                currentCategory = cat;
                                needsAssign = false;
                            }
                            categories.button(ui.getIcon(cat.name()), Styles.clearTogglei, () -> {
                                currentCategory = cat;
                                if(control.input.block != null) control.input.block = getSelectedBlock(currentCategory);
                                rebuildCategory.run();
                            }).group(group).update(i -> ((ImageButton)i).setChecked(currentCategory == cat)).name("category-" + cat.name());
                        }
                    }).fillY().bottom().touchable(Touchable.enabled);
                }

                // unit toggle button
                toggler.table(select -> {
                    select.image().color(Pal.accent).growX().height(4).colspan(2).row();
                    select.button(Icon.units, Styles.clearTogglei, () -> {
                        control.input.unitPlacementMode = !control.input.unitPlacementMode;
                        control.input.unitType = null;
                        control.input.block = null;
                    }).checked(b -> control.input.unitPlacementMode).size(50f);
                }).bottom().right();

                rebuildCategory.run();
            });
        });
    }

    public void buildUnitTable(Table table){
        Seq<UnitType> units = content.units().select(u -> u.unlockedNow() && !u.isHidden());
        int cols = 4;
        int row = 0;
        for(UnitType type : units){
            if(row % cols == 0) table.row();
            ImageButton btn = table.button(new TextureRegionDrawable(type.uiIcon), Styles.selecti, () -> {
                control.input.unitType = type;
                control.input.block = null;
            }).size(46f).get();
            btn.resizeImage(iconMed);
            btn.hovered(() -> menuHoverBlock = null);
            row++;
        }
    }

    Seq<Category> getCategories(){
        return returnCatArray.clear().addAll(Category.all).sort((c1, c2) -> Boolean.compare(categoryEmpty[c1.ordinal()], categoryEmpty[c2.ordinal()]));
    }

    Seq<Block> getByCategory(Category cat){
        return returnArray.selectFrom(content.blocks(), block -> block.category == cat && block.isVisible() && block.environmentBuildable());
    }

    Seq<Block> getUnlockedByCategory(Category cat){
        return returnArray2.selectFrom(content.blocks(), block -> block.category == cat && block.isVisible() && unlocked(block)).sort((b1, b2) -> Boolean.compare(!b1.isPlaceable(), !b2.isPlaceable()));
    }

    Block getSelectedBlock(Category cat){
        return selectedBlocks.get(cat, () -> getByCategory(cat).find(this::unlocked));
    }

    boolean unlocked(Block block){
        return block.unlockedNow() && block.placeablePlayer && block.environmentBuildable() && block.supportsEnv(state.rules.env);
    }

    boolean hasInfoBox(){
        hover = hovered();
        return control.input.block != null || menuHoverBlock != null || hover != null || control.input.unitType != null;
    }

    @Nullable
    Displayable hovered(){
        Vec2 v = topTable.stageToLocalCoordinates(Core.input.mouse());
        if(Core.scene.hasMouse() || topTable.hit(v.x, v.y, false) != null) return null;

        Unit unit = Units.closestOverlap(player.team(), Core.input.mouseWorldX(), Core.input.mouseWorldY(), 5f, u -> !u.isLocal() && u.displayable());
        if(unit != null) return unit;

        Tile hoverTile = world.tileWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);
        if(hoverTile != null){
            if(hoverTile.build != null && hoverTile.build.displayable() && !hoverTile.build.inFogTo(player.team())){
                return nextFlowBuild = hoverTile.build;
            }
            if((hoverTile.drop() != null && hoverTile.block() == Blocks.air) || hoverTile.wallDrop() != null || hoverTile.floor().liquidDrop != null){
                return hoverTile;
            }
        }
        return null;
    }
}