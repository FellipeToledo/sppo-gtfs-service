package com.fajtech.sppogtfs.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "shapes")
@IdClass(ShapePointEntity.Key.class)
public class ShapePointEntity {

    @Id
    @Column(name = "shape_id")
    private String shapeId;

    @Id
    @Column(name = "shape_pt_sequence")
    private int shapePtSequence;

    @Column(name = "shape_pt_lat")
    private double shapePtLat;

    @Column(name = "shape_pt_lon")
    private double shapePtLon;

    protected ShapePointEntity() {
    }

    public String getShapeId() {
        return shapeId;
    }

    public int getShapePtSequence() {
        return shapePtSequence;
    }

    public double getShapePtLat() {
        return shapePtLat;
    }

    public double getShapePtLon() {
        return shapePtLon;
    }

    public static class Key implements Serializable {
        private String shapeId;
        private int shapePtSequence;

        public Key() {
        }

        public Key(String shapeId, int shapePtSequence) {
            this.shapeId = shapeId;
            this.shapePtSequence = shapePtSequence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return shapePtSequence == key.shapePtSequence && Objects.equals(shapeId, key.shapeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shapeId, shapePtSequence);
        }
    }
}
