package com.miniagoda.hotel.entity;

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
    private String city;
    
    @Column(nullable = false)
    private String address;
    
    @Column(length = 1000)
    private String description;

    @Column(unique = true, nullable = false)
    private String code;

    @OneToMany(mappedBy = "hotel")
    private List<RoomType> roomTypes;
}