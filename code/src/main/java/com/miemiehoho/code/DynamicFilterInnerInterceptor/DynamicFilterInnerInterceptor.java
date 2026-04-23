package com.miemiehoho.code.DynamicFilterInnerInterceptor;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 生产级动态过滤拦截器
 * 结合 MyBatis-Plus 官方 JsqlParserSupport 实现安全、可靠的 SQL 重写
 */
public class DynamicFilterInnerInterceptor extends JsqlParserSupport implements InnerInterceptor {

    /**
     * 【防列名注入校验】：允许以字母或下划线开头，后续为字母、数字或下划线
     */
    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // 1. 提取 IPageQuery 参数
        IPageQuery<?> pageQuery = extractPageQuery(parameter);

        if (pageQuery != null && pageQuery.filters() != null && !pageQuery.filters().isEmpty()) {
            // 2. 将条件列表构建为一棵 AST 表达式树
            Expression filterExpression = buildWhereExpression(pageQuery.filters());

            if (filterExpression != null) {
                // 3. 获取 MyBatis-Plus 包装的 BoundSql 工具
                PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
                
                // 4. 调用父类 JsqlParserSupport 的能力解析并重写 SQL
                // 第一个参数是原 SQL，第二个参数透传给 processSelect 方法
                String newSql = this.parserSingle(mpBs.sql(), filterExpression);
                mpBs.sql(newSql);
            }
        }
    }

    /**
     * 重写父类方法：处理 SELECT 语句的 AST 修改
     */
    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        if (obj instanceof Expression) {
            Expression additionalWhere = (Expression) obj;
            SelectBody selectBody = select.getSelectBody();
            
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) selectBody;
                Expression currentWhere = plainSelect.getWhere();
                
                // 如果原 SQL 已有 WHERE 条件，使用 AND 拼接；否则直接设置为 WHERE
                if (currentWhere == null) {
                    plainSelect.setWhere(additionalWhere);
                } else {
                    plainSelect.setWhere(new AndExpression(currentWhere, additionalWhere));
                }
            }
        }
    }

    /**
     * 构建总的 WHERE 表达式树
     */
    private Expression buildWhereExpression(List<FilterItem> filters) {
        Expression rootExpression = null;

        for (FilterItem filter : filters) {
            String columnName = filter.getColumn();
            if (StringUtils.isBlank(columnName)) continue;

            // 1. 列名驼峰转下划线
            if (filter.isUnderlineConvert()) {
                columnName = StringUtils.camelToUnderline(columnName);
            }

            // 2. 严格的列名安全校验 (防注入)
            if (!COLUMN_NAME_PATTERN.matcher(columnName).matches()) {
                throw new IllegalArgumentException("非法的过滤字段名 (存在 SQL 注入风险): " + columnName);
            }

            Column column = new Column(columnName);

            // 3. 使用枚举策略构建具体的表达式
            FilterOperatorEnum strategy = FilterOperatorEnum.fromOperator(filter.getOperator());
            Expression condition = strategy.build(column, filter.getValue());

            // 4. 使用 AND 组合多个过滤条件
            if (rootExpression == null) {
                rootExpression = condition;
            } else {
                rootExpression = new AndExpression(rootExpression, condition);
            }
        }
        return rootExpression;
    }

    /**
     * 提取 IPageQuery 参数，兼容 @Param 包装的 Map 场景
     */
    private IPageQuery<?> extractPageQuery(Object parameter) {
        if (parameter instanceof IPageQuery) {
            return (IPageQuery<?>) parameter;
        }
        if (parameter instanceof Map) {
            for (Object value : ((Map<?, ?>) parameter).values()) {
                if (value instanceof IPageQuery) {
                    return (IPageQuery<?>) value;
                }
            }
        }
        return null;
    }
}