/**
 * Application layer: use cases ({@code port.in}), outbound ports ({@code port.out}) and the
 * deterministic business rules that implement them ({@code service}).
 * <p>
 * Depends only on the domain. Adapters for databases, HTTP APIs and AI live in infrastructure;
 * controllers live in presentation.
 */
package com.reservationagent.hotelreservationagent.application;
