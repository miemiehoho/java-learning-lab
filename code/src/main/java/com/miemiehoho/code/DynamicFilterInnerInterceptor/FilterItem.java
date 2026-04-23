package com.miemiehoho.code.DynamicFilterInnerInterceptor;

/**
 * 过滤条件项，对应前端 filters 数组中的每个对象。
 */
public class FilterItem {

    /**
     * 数据库字段名（Java 属性名，驼峰命名）
     */
    private String column;

    /**
     * 操作符，如 "="、">"、"LIKE" 等
     */
    private String operator;

    /**
     * 条件值
     */
    private Object value;

    /**
     * 是否将 column 从驼峰转为下划线命名（默认 true）
     */
    private boolean underlineConvert = true;

    // ========== Getter & Setter ==========
    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isUnderlineConvert() {
        return underlineConvert;
    }

    public void setUnderlineConvert(boolean underlineConvert) {
        this.underlineConvert = underlineConvert;
    }
}