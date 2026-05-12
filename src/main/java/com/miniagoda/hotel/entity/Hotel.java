package com.miniagoda.hotel.entity;

import java.math.BigDecimal;
import java.util.List;

import com.miniagoda.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "hotels")
public class Hotel extends BaseEntity {
    
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = true, precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(nullable = false, unique = true)
    private String code;

    @OneToMany(mappedBy = "hotel")
    List<RoomType> roomTypes;
}