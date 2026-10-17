/**
 * PostgreSQL adapters, one sub-package per model. JPA entities never leave this package tree;
 * adapters map them to domain records. Spatial queries (hotel index, municipalities) use JdbcClient
 * with PostGIS SQL.
 */
package com.reservationagent.hotelreservationagent.infrastructure.persistence;
