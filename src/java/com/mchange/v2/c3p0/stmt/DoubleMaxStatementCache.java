/*
 * Distributed as part of c3p0 v.0.9.5.3
 *
 * Copyright (C) 2018 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.c3p0.stmt;

import java.sql.*;
import com.mchange.v2.async.AsynchronousRunner;

public final class DoubleMaxStatementCache extends GooGooStatementCache
{
    //MT: protected by this' lock
    int max_statements;
    Deathmarch globalDeathmarch = new Deathmarch();

    int max_statements_per_connection;
    DeathmarchConnectionStatementManager dcsm;

    public DoubleMaxStatementCache(AsynchronousRunner blockingTaskAsyncRunner, AsynchronousRunner deferredStatementDestroyer, int max_statements, int max_statements_per_connection)
    {
	super( blockingTaskAsyncRunner, deferredStatementDestroyer );
	this.max_statements = max_statements;
	this.max_statements_per_connection = max_statements_per_connection;
    }

    //called only in parent's constructor
    protected ConnectionStatementManager createConnectionStatementManager()
    { return (this.dcsm = new DeathmarchConnectionStatementManager()); }

    //called by parent only with this' lock
    void addStatementToDeathmarches( Object pstmt, Connection physicalConnection )
    {
	globalDeathmarch.deathmarchStatement( pstmt );
	dcsm.getDeathmarch( physicalConnection ).deathmarchStatement( pstmt ); 
    }

    void removeStatementFromDeathmarches( Object pstmt, Connection physicalConnection )
    { 
	globalDeathmarch.undeathmarchStatement( pstmt );
	dcsm.getDeathmarch( physicalConnection ).undeathmarchStatement( pstmt ); 
    }

    boolean prepareAssimilateNewStatement(Connection pcon)
    {
	int cxn_stmt_count = dcsm.getNumStatementsForConnection( pcon );
	if (cxn_stmt_count < max_statements_per_connection) //okay... we can cache another for the connection, but how 'bout globally?
	    {
		int global_size = this.countCachedStatements();
		return (  global_size < max_statements || (global_size == max_statements && globalDeathmarch.cullNext()) );
	    }
	else //we can only cache if we can clear one from the Connection (which implies clearing one globally, so we needn't check max_statements)
	    return (cxn_stmt_count == max_statements_per_connection && dcsm.getDeathmarch( pcon ).cullNext());
    }
}
