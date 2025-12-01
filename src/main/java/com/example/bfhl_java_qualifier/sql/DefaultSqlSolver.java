package com.example.bfhl_java_qualifier.sql;

import org.springframework.stereotype.Component;

@Component
public class DefaultSqlSolver implements SqlSolver {

    @Override
    public String getFinalQuery(boolean ignored) {
        // Always return this one query
        return """
                WITH employee_totals AS (
                    SELECT
                        d.DEPARTMENT_NAME,
                        e.EMP_ID,
                        e.FIRST_NAME,
                        e.LAST_NAME,
                        e.DOB,
                        SUM(p.AMOUNT) AS SALARY,
                        ROW_NUMBER() OVER (
                            PARTITION BY d.DEPARTMENT_ID
                            ORDER BY SUM(p.AMOUNT) DESC
                        ) AS rn
                    FROM EMPLOYEE e
                    JOIN DEPARTMENT d
                        ON e.DEPARTMENT = d.DEPARTMENT_ID
                    JOIN PAYMENTS p
                        ON p.EMP_ID = e.EMP_ID
                    WHERE DAY(p.PAYMENT_TIME) <> 1
                    GROUP BY
                        d.DEPARTMENT_ID,
                        d.DEPARTMENT_NAME,
                        e.EMP_ID,
                        e.FIRST_NAME,
                        e.LAST_NAME,
                        e.DOB
                )
                SELECT
                    DEPARTMENT_NAME,
                    SALARY,
                    CONCAT(FIRST_NAME, ' ', LAST_NAME) AS EMPLOYEE_NAME,
                    TIMESTAMPDIFF(YEAR, DOB, CURDATE()) AS AGE
                FROM employee_totals
                WHERE rn = 1
                ORDER BY DEPARTMENT_NAME;
                """;
    }
}
