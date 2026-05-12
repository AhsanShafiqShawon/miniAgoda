package com.miniagoda.inventory.entity;

import java.time.LocalDate;

import com.miniagoda.common.entity.BaseEntity;
import com.miniagoda.hotel.entity.RoomType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "inventories", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_type_id", "date"})
    }
)
public class Inventory extends BaseEntity {
    
    @Column(nullable = false)
    private Integer availableUnits;

    @Column(nullable = false)
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;
}