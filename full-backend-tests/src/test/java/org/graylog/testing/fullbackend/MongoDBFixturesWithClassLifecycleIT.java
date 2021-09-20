/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.testing.fullbackend;

import io.restassured.specification.RequestSpecification;
import org.graylog.testing.completebackend.GraylogBackend;
import org.graylog.testing.containermatrix.annotations.ContainerMatrixTest;
import org.graylog.testing.containermatrix.annotations.ContainerMatrixTestsConfiguration;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.graylog.testing.completebackend.Lifecycle.CLASS;

@ContainerMatrixTestsConfiguration(serverLifecycle = CLASS, esVersions = {"6.8.4"}, mongoDBFixtures = "access-token.json")
class MongoDBFixturesWithClassLifecycleIT {
    private final GraylogBackend sut;
    private final RequestSpecification requestSpec;

    public MongoDBFixturesWithClassLifecycleIT(GraylogBackend sut, RequestSpecification requestSpec) {
        this.sut = sut;
        this.requestSpec = requestSpec;
    }

    @ContainerMatrixTest
    void oneTokenPresentWithTestMethodA() {
        assertTokenPresent();
    }

    @ContainerMatrixTest
    void oneTokenPresentWithTestMethodB() {
        assertTokenPresent();
    }

    private void assertTokenPresent() {
        List<?> tokens = given().spec(requestSpec)
                .when()
                .get("users/local:admin/tokens")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("tokens");

        assertThat(tokens).hasSize(1);
    }

}
