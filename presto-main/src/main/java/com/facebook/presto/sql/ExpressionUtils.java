package com.facebook.presto.sql;

import com.facebook.presto.sql.planner.Symbol;
import com.facebook.presto.sql.tree.Expression;
import com.facebook.presto.sql.tree.LogicalBinaryExpression;
import com.facebook.presto.sql.tree.QualifiedNameReference;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.facebook.presto.sql.planner.DeterminismEvaluator.deterministic;
import static com.facebook.presto.sql.tree.BooleanLiteral.TRUE_LITERAL;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;

public class ExpressionUtils
{
    public static List<Expression> extractConjuncts(Expression expression)
    {
        if (expression instanceof LogicalBinaryExpression && ((LogicalBinaryExpression) expression).getType() == LogicalBinaryExpression.Type.AND) {
            LogicalBinaryExpression and = (LogicalBinaryExpression) expression;
            return ImmutableList.<Expression>builder()
                    .addAll(extractConjuncts(and.getLeft()))
                    .addAll(extractConjuncts(and.getRight()))
                    .build();
        }

        return ImmutableList.of(expression);
    }

    public static Expression and(Expression... expressions)
    {
        return and(Arrays.asList(expressions));
    }

    public static Expression and(Iterable<Expression> expressions)
    {
        return binaryExpression(LogicalBinaryExpression.Type.AND, expressions);
    }

    public static Expression or(Expression... expressions)
    {
        return or(Arrays.asList(expressions));
    }

    public static Expression or(Iterable<Expression> expressions)
    {
        return binaryExpression(LogicalBinaryExpression.Type.OR, expressions);
    }

    public static Expression binaryExpression(LogicalBinaryExpression.Type type, Iterable<Expression> expressions)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(expressions, "expressions is null");
        Preconditions.checkArgument(!Iterables.isEmpty(expressions), "expressions is empty");

        Iterator<Expression> iterator = expressions.iterator();

        Expression result = iterator.next();
        while (iterator.hasNext()) {
            result = new LogicalBinaryExpression(type, result, iterator.next());
        }

        return result;
    }

    public static Expression combineConjuncts(Expression... expressions)
    {
        return combineConjuncts(Arrays.asList(expressions));
    }

    public static Expression combineConjuncts(Iterable<Expression> expressions)
    {
        Preconditions.checkNotNull(expressions, "expressions is null");

        // Flatten all the expressions into their component conjuncts
        expressions = Iterables.concat(Iterables.transform(expressions, new Function<Expression, Iterable<Expression>>()
        {
            @Override
            public Iterable<Expression> apply(Expression expression)
            {
                return extractConjuncts(expression);
            }
        }));

        // Strip out all true literal conjuncts
        expressions = Iterables.filter(expressions, not(Predicates.<Expression>equalTo(TRUE_LITERAL)));

        // Capture all non-deterministic conjuncts
        Iterable<Expression> nonDeterministicConjuncts = Iterables.filter(expressions, not(deterministic()));

        // Capture and de-dupe all deterministic conjuncts
        Iterable <Expression> deterministicConjuncts = ImmutableSet.copyOf(Iterables.filter(expressions, deterministic()));

        expressions = Iterables.concat(nonDeterministicConjuncts, deterministicConjuncts);
        return Iterables.isEmpty(expressions) ? TRUE_LITERAL : and(expressions);
    }

    public static Function<Symbol, QualifiedNameReference> symbolToQualifiedNameReference()
    {
        return new Function<Symbol, QualifiedNameReference>() {
            @Override
            public QualifiedNameReference apply(Symbol symbol)
            {
                return new QualifiedNameReference(symbol.toQualifiedName());
            }
        };
    }

    public static Expression stripNonDeterministicConjuncts(Expression expression)
    {
        return combineConjuncts(filter(extractConjuncts(expression), deterministic()));
    }
}