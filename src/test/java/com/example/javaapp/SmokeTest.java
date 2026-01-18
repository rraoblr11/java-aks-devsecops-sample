package com.example.javaapp;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("smoke")
class SmokeTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void testApplicationHealth() {
        given()
            .when()
            .get("/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void testHealthEndpoint() {
        given()
            .when()
            .get("/api/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void testGetAllUsers() {
        given()
            .when()
            .get("/api/v1/users")
            .then()
            .statusCode(200);
    }

    @Test
    void testInfoEndpoint() {
        given()
            .when()
            .get("/api/info")
            .then()
            .statusCode(200)
            .body("application", equalTo("Java AKS Sample App"));
    }
}
