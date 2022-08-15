/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.e2e.flink.v2.jdbc;

import org.apache.seatunnel.e2e.flink.FlinkContainer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

public class JdbcSourceToConsoleIT extends FlinkContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcSourceToConsoleIT.class);
    private PostgreSQLContainer<?> psl;

    @SuppressWarnings("checkstyle:MagicNumber")
    @BeforeEach
    public void startPostgreSqlContainer() throws InterruptedException, ClassNotFoundException, SQLException {
        psl = new PostgreSQLContainer<>(DockerImageName.parse("postgres:alpine3.16"))
                .withNetwork(NETWORK)
                .withNetworkAliases("postgresql")
                .withLogConsumer(new Slf4jLogConsumer(LOGGER));
        Startables.deepStart(Stream.of(psl)).join();
        LOGGER.info("PostgreSql container started");
        Thread.sleep(5000L);
        Class.forName(psl.getDriverClassName());
        initializeJdbcTable();
        batchInsertData();
    }

    private void initializeJdbcTable() {
        try (Connection connection = DriverManager.getConnection(psl.getJdbcUrl(), psl.getUsername(), psl.getPassword())) {
            Statement statement = connection.createStatement();
            String sql = "CREATE TABLE test (\n" +
                    "  name varchar(255) NOT NULL\n" +
                    ")";
            statement.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Initializing PostgreSql table failed!", e);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void batchInsertData() throws SQLException {
        try (Connection connection = DriverManager.getConnection(psl.getJdbcUrl(), psl.getUsername(), psl.getPassword())) {
            String sql = "insert into test(name) values(?)";
            connection.setAutoCommit(false);
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            for (int i = 0; i < 10; i++) {
                preparedStatement.setString(1, "Mike");
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert data failed!", e);
        }
    }

    @Test
    public void testFakeSourceToJdbcSink() throws SQLException, IOException, InterruptedException {
        Container.ExecResult execResult = executeSeaTunnelFlinkJob("/jdbc/jdbcsource_to_console.conf");
        Assertions.assertEquals(0, execResult.getExitCode());
    }

    @AfterEach
    public void closePostgreSqlContainer() {
        if (psl != null) {
            psl.stop();
        }
    }
}