package com.fajtech.sppogtfs.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TripJpaRepository extends JpaRepository<TripEntity, String> {

    /** Only trips that reference a shape; drops the unused columns via a projection. */
    @Query("select t from TripEntity t where t.shapeId is not null")
    List<TripEntity> findAllWithShape();
}
