package com.facebook.presto.sql.compiler;

import com.facebook.presto.sql.tree.Relation;
import com.facebook.presto.sql.tree.Subquery;
import com.facebook.presto.sql.tree.Table;

import java.util.IdentityHashMap;
import java.util.Map;

class AnalysisContext
{
    private final SymbolAllocator symbolAllocator;

    private final IdentityHashMap<Subquery, AnalysisResult> inlineViews = new IdentityHashMap<>();
    private final IdentityHashMap<Relation, TupleDescriptor> tableDescriptors = new IdentityHashMap<>();

    public AnalysisContext()
    {
        this(new SymbolAllocator());
    }

    public AnalysisContext(SymbolAllocator symbolAllocator)
    {
        this.symbolAllocator = symbolAllocator;
    }

    /**
     * We really want to expose an unmodifiable identity map here. Unfortunately there's no such a thing, so we expose the raw reference.
     * Callers should *not* modify its contents.
     */
    IdentityHashMap<Subquery, AnalysisResult> getInlineViews()
    {
        return inlineViews;
    }

    public void registerInlineView(Subquery node, AnalysisResult analysis)
    {
        inlineViews.put(node, analysis);
    }

    public void registerTable(Table table, TupleDescriptor descriptor)
    {
        tableDescriptors.put(table, descriptor);
    }

    /**
     * We really want to expose an unmodifiable identity map here. Unfortunately there's no such a thing, so we expose the raw reference.
     * Callers should *not* modify its contents.
     */
    IdentityHashMap<Relation, TupleDescriptor> getTableDescriptors()
    {
        return tableDescriptors;
    }

    public SymbolAllocator getSymbolAllocator()
    {
        return symbolAllocator;
    }

    public Map<Symbol, Type> getSymbols()
    {
        return symbolAllocator.getTypes();
    }
}