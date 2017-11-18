package com.github.wmxd

import kotliquery.HikariCP
import kotliquery.sessionOf

fun dbSession(name: String = "default") = sessionOf(HikariCP.dataSource(name))