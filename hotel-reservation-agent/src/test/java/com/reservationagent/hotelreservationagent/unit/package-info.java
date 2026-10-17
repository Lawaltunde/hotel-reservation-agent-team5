/**
 * Fast unit tests: plain JUnit 5 + AssertJ, no Spring context, no Docker.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Name classes {@code *Test}; they run with Surefire in the {@code test} phase.</li>
 *   <li>Mirror the main package (for example {@code unit.domain.ranking}).</li>
 *   <li>Anything that needs a database or the Spring context is an {@code *IT} in
 *       {@code ..integration..} and runs with Failsafe.</li>
 * </ul>
 */
package com.reservationagent.hotelreservationagent.unit;
