package com.miemiehoho.code.DynamicFilterInnerInterceptor;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 过滤操作符策略枚举 (严格适配 JSqlParser 4.4 API)
 * 采用策略模式，将不同操作符的 AST 节点构建逻辑隔离，消除冗长的 if-else / switch-case
 */
public enum FilterOperatorEnum {

    EQ("=") {
        @Override
        public Expression build(Column column, Object value) {
            EqualsTo exp = new EqualsTo();
            exp.setLeftExpression(column);
            exp.setRightExpression(toSafeValue(value));
            return exp;
        }
    },
    NE("!=") {
        @Override
        public Expression build(Column column, Object value) {
            NotEqualsTo exp = new NotEqualsTo();
            exp.setLeftExpression(column);
            exp.setRightExpression(toSafeValue(value));
            return exp;
        }
    },
    GT(">") {
        @Override
        public Expression build(Column column, Object value) {
            GreaterThan exp = new GreaterThan();
            exp.setLeftExpression(column);
            exp.setRightExpression(toSafeValue(value));
            return exp;
        }
    },
    LT("<") {
        @Override
        public Expression build(Column column, Object value) {
            MinorThan exp = new MinorThan();
            exp.setLeftExpression(column);
            exp.setRightExpression(toSafeValue(value));
            return exp;
        }
    },
    GE(">=") {
        @Override
        public Expression build(Column column, Object value) {
            GreaterThanEquals exp = new GreaterThanEquals();
            exp.setLeftExpression(column);
            exp.setRightExpression(toSafeValue(value));
            return exp;
        }
    },
    LE("<=") {
        @Override
        public Expression build(Column column, Object value) {
            MinorThanEquals exp = new MinorThanEquals();
            exp.setLeftExpression(column);
            exp.setRightExpression(toSafeValue(value));
            return exp;
        }
    },
    LIKE("LIKE") {
        @Override
        public Expression build(Column column, Object value) {
            LikeExpression exp = new LikeExpression();
            exp.setLeftExpression(column);
            // 模糊查询自动包裹 %，并转义内部单引号防止注入
            String safeValue = escapeSqlString(String.valueOf(value));
            exp.setRightExpression(new StringValue("%" + safeValue + "%"));
            return exp;
        }
    },
    IN("IN") {
        @Override
        public Expression build(Column column, Object value) {
            return buildInExpression(column, value, false);
        }
    },
    NOT_IN("NOT IN") {
        @Override
        public Expression build(Column column, Object value) {
            return buildInExpression(column, value, true);
        }
    },
    IS_NULL("IS NULL") {
        @Override
        public Expression build(Column column, Object value) {
            IsNullExpression exp = new IsNullExpression();
            exp.setLeftExpression(column);
            exp.setNot(false);
            return exp;
        }
    },
    IS_NOT_NULL("IS NOT NULL") {
        @Override
        public Expression build(Column column, Object value) {
            IsNullExpression exp = new IsNullExpression();
            exp.setLeftExpression(column);
            exp.setNot(true);
            return exp;
        }
    };

    private final String operator;

    FilterOperatorEnum(String operator) {
        this.operator = operator;
    }

    public String getOperator() {
        return operator;
    }

    /**
     * 匹配操作符并返回对应的策略
     */
    public static FilterOperatorEnum fromOperator(String operator) {
        if (operator == null) return null;
        String upperOp = operator.trim().toUpperCase();
        for (FilterOperatorEnum opEnum : values()) {
            if (opEnum.getOperator().equals(upperOp)) {
                return opEnum;
            }
        }
        throw new IllegalArgumentException("不支持的过滤操作符: " + operator);
    }

    public abstract Expression build(Column column, Object value);

    // ================== 公共防注入与类型转换工具 ==================

    /**
     * 将 Java 对象转换为安全的 JSqlParser AST 值节点
     * 【核心防注入】：处理单引号转义
     */
    protected static Expression toSafeValue(Object value) {
        if (value == null) {
            return new NullValue();
        }
        if (value instanceof Number) {
            return new LongValue(value.toString());
        }
        if (value instanceof Boolean) {
            return new LongValue((Boolean) value ? 1 : 0);
        }
        // 字符串类型必须替换单引号为双单引号，防止 '1'='1' 闭合注入
        return new StringValue(escapeSqlString(value.toString()));
    }

    protected static String escapeSqlString(String input) {
        return input == null ? null : input.replace("'", "''");
    }

    /**
     * 构建 IN / NOT IN 表达式
     */
    protected static Expression buildInExpression(Column column, Object value, boolean isNot) {
        ExpressionList expressionList = new ExpressionList();
        List<Expression> expressions = new ArrayList<>();

        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                expressions.add(toSafeValue(item));
            }
        } else {
            // 如果传入的不是集合，兼容处理为单元素
            expressions.add(toSafeValue(value));
        }
        expressionList.setExpressions(expressions);

        InExpression inExpression = new InExpression();
        inExpression.setLeftExpression(column);
        inExpression.setRightItemsList(expressionList); // 4.4 版本使用 setRightItemsList
        inExpression.setNot(isNot);
        return inExpression;
    }
}