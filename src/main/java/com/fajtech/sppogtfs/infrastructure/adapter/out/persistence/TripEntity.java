package com.fajtech.sppogtfs.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trips")
public class TripEntity {

    @Id
    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "route_id")
    private String routeId;

    @Column(name = "shape_id")
    private String shapeId;

    @Column(name = "direction_id")
    private Integer directionId;

    @Column(name = "trip_headsign")
    private String tripHeadsign;

    protected TripEntity() {
    }

    public String getTripId() {
        return tripId;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getShapeId() {
        return shapeId;
    }

    public Integer getDirectionId() {
        return directionId;
    }

    public String getTripHeadsign() {
        return tripHeadsign;
    }
}
