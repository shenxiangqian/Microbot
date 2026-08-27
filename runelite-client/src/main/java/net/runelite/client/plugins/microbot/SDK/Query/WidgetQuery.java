package net.runelite.client.plugins.microbot.SDK.Query;

import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.SDK.Entity.WidgetModel;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WidgetQuery {
    private Predicate<WidgetModel> widgetModelPredicate = e->true;
    private int[] rootIds;
    private int[] indexPathIds;
    private boolean hasAnyAction(WidgetModel widget,String... actions){
        if(widget == null || widget.getWidget().getActions() == null || actions ==null)return false;
        return Arrays.stream(widget.getWidget().getActions())
                .filter(Objects::nonNull)
                .anyMatch(e->Arrays.stream(actions).filter(Objects::nonNull).anyMatch(e::equalsIgnoreCase));
    }
    private Stream<WidgetModel> buildWidgetStream(){
        if(rootIds != null){
            if(rootIds.length == 1){
                int groupId = rootIds[0];
                //noinspection MagicConstant
                Widget rootWidget = Microbot.getClient().getWidget(groupId,0);
                if(rootWidget == null)return Stream.empty();
                return getAllDescendants(rootWidget).map(WidgetModel::of);
            }else {
                Widget rootWidget = getWidget(rootIds);
                if(rootWidget == null)return Stream.empty();
                return getAllDescendants(rootWidget).map(WidgetModel::of);
            }
        }
        else if(indexPathIds != null){
            var wd = getWidget(indexPathIds);
            return Optional.ofNullable(wd).map(WidgetModel::of).stream();
        }
        return getAllWidgets().stream().map(WidgetModel::of);
    }
    private List<Widget> getAllWidgets(){
        List<Widget> result = new ArrayList<>();
        for (Widget root : Arrays.stream(Microbot.getClient().getWidgetRoots()).collect(Collectors.toList())) {
            collectWidgets(root, result);
        }
        return result;
    }

    private void collectWidgets(Widget widget,List<Widget> result){
        if(widget == null)return;
        result.add(widget);
        Widget[][] childGroups = {
                widget.getChildren(),
                widget.getNestedChildren(),
                widget.getDynamicChildren(),
                widget.getStaticChildren()
        };

        for (Widget[] children : childGroups) {
            if (children == null) {
                continue;
            }
            for (Widget child : Arrays.stream(children).collect(Collectors.toList())) {
                collectWidgets(child, result);
            }
        }

    }

    private Widget getWidget(int... ids){
        if(ids == null || ids.length ==0)return null;
        Widget widget;
        if(ids.length == 1){
            widget = Microbot.getClient().getWidget(ids[0],0);
            return widget;
        }
        widget = Microbot.getClient().getWidget(ids[0],ids[1]);
        if(widget == null)return null;
        for(int i = 2; i < ids.length; i++){
            Widget[] children = widget.getChildren();
            if(children == null || ids[i] >= children.length || ids[i] < 0)return null;
            widget = children[ids[i]];
        }
        return widget;
    }
    private Stream<Widget> getAllDescendantsFromArrays(Widget widget,int depth){
        if(widget == null || depth >100)return Stream.empty();
        Widget[][] childGroups = {
                widget.getChildren(),
                widget.getNestedChildren(),
                widget.getDynamicChildren(),
                widget.getStaticChildren()
        };
        Stream<Widget> descendantsStream = Arrays.stream(childGroups)
                .filter(Objects::nonNull)
                .flatMap(children -> Arrays.stream(children)
                        .filter(Objects::nonNull)
                        .flatMap(child -> getAllDescendantsFromArrays(child, depth + 1)));
        return Stream.concat(Stream.of(widget),descendantsStream);
    }
    private Stream<Widget> getAllDescendants(Widget widget){
        if(widget == null)return Stream.empty();
        return getAllDescendantsFromArrays(widget,0);
    }

    public WidgetQuery filter(Predicate<WidgetModel> filter){
        widgetModelPredicate = widgetModelPredicate.and(filter);
        return this;
    }
    public WidgetQuery inIndexPath(int... id){
        this.indexPathIds = id;
        return this;
    }
    public WidgetQuery inRoots(int... id){
        this.rootIds = id;
        return this;
    }
    public WidgetQuery isVisible(){
        widgetModelPredicate = widgetModelPredicate.and(e->!e.getWidget().isHidden());
        return this;
    }
    public WidgetQuery actionEquals(String... action){
        widgetModelPredicate = widgetModelPredicate.and(e->hasAnyAction(e,action));
        return this;
    }
    public WidgetQuery actionContains(String... action){
        widgetModelPredicate = widgetModelPredicate.and(e->Arrays.stream(action)
                .filter(Objects::nonNull)
                .anyMatch(s->{
                    var actions = e.getWidget().getActions();
                    if(actions == null || actions.length == 0)return false;
                    return Arrays.stream(actions)
                            .filter(Objects::nonNull)
                            .anyMatch(c->c.toLowerCase().contains(s.toLowerCase()));
                }));
        return this;
    }
    public WidgetQuery textContains(String text){
        widgetModelPredicate = widgetModelPredicate.and(e->{
            var txt = e.getWidget().getText();
            return !txt.isEmpty() && txt.contains(text);
        });
        return this;
    }
    public WidgetQuery contains(String text){
        widgetModelPredicate = widgetModelPredicate.and(e->e.getWidget().getText().contains(text) || e.getWidget().getName().contains(text) || (e.getWidget().getActions()!=null && Arrays.stream(e.getWidget().getActions()).anyMatch(c->c!=null && c.contains(text))));
        return this;
    }
    public WidgetQuery itemId(int... itemId){
        widgetModelPredicate = widgetModelPredicate.and(e->Arrays.stream(itemId).anyMatch(c->e.getWidget().getItemId() == c));
        return this;
    }
    public Optional<WidgetModel> findFirst(){
        return Microbot.getClientThread().runOnClientThreadOptional(()->buildWidgetStream().filter(widgetModelPredicate).findFirst()).orElse(null);
    }
    public List<WidgetModel> findAll(){
        return Microbot.getClientThread().runOnClientThreadOptional(()->buildWidgetStream().filter(widgetModelPredicate).collect(Collectors.toList())).orElse(Collections.emptyList());
    }
    public boolean isAny(){
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                buildWidgetStream().anyMatch(widgetModelPredicate)
        ).orElse(false);
    }
}
