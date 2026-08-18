package com.tonewire.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Connected outputs; selected route is null if public APIs cannot identify one unambiguously. */
public final class RouteSnapshot {
    private final List<AudioRoute> routes;
    private final AudioRoute selectedRoute;

    public RouteSnapshot(List<AudioRoute> routes) {
        this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
        this.selectedRoute = routes.size() == 1 ? routes.get(0) : null;
    }
    public List<AudioRoute> getRoutes() { return routes; }
    public AudioRoute getSelectedRoute() { return selectedRoute; }
    public boolean isAmbiguous() { return routes.size() != 1; }
}
