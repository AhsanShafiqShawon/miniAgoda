package com.miniagoda.hotel.entity;

import java.math.BigDecimal;
import java.util.List;

import com.miniagoda.booking.entity.Booking;
import com.miniagoda.common.entity.BaseEntity;
import com.miniagoda.inventory.entity.Inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "room_type")
public class RoomType extends BaseEntity {
    
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer totalUnits;
    
    @Column(nullable = false)
    private BigDecimal price;

    @ManyToOne
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @OneToMany(mappedBy = "roomType")
    private List<Inventory> inventory;

    @OneToMany(mappedBy = "roomType")
    private List<Booking> bookings;
}