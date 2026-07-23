package com.fajtech.sppogtfs.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Optional GTFS {@code feed_info} row. Only the first row is used.
 */
@Entity
@Table(name = "feed_info")
public class FeedInfoEntity {

    @Id
    @Column(name = "feed_publisher_name")
    private String feedPublisherName;

    @Column(name = "feed_version")
    private String feedVersion;

    @Column(name = "feed_start_date")
    private LocalDate feedStartDate;

    protected FeedInfoEntity() {
    }

    public String getFeedPublisherName() {
        return feedPublisherName;
    }

    public String getFeedVersion() {
        return feedVersion;
    }

    public LocalDate getFeedStartDate() {
        return feedStartDate;
    }
}
