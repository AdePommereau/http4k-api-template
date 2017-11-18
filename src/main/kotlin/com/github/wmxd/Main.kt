package com.github.wmxd

import com.fasterxml.jackson.annotation.JsonProperty
import kotliquery.*
import org.http4k.core.*
import org.http4k.filter.CachingFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Undertow
import org.http4k.server.asServer
import java.sql.SQLException


val timingFilter = Filter { next: HttpHandler ->
    { request: Request ->
        val start = System.currentTimeMillis()
        val response = next(request)
        val latency = System.currentTimeMillis() - start
        println("Request to ${request.uri} took ${latency}ms with status ${response.status}")
        response
    }
}

val sqlExceptionHandler = Filter { next ->
    { request ->
        try {
            next(request)
        } catch (e: SQLException) {
            e.printStackTrace()
            Response(Status.INTERNAL_SERVER_ERROR).body("Error within database!")
        }
    }
}


data class PersonRequest(@JsonProperty("name") val name: String)

data class Person(@JsonProperty("id") val id: Int, @JsonProperty("name") val name: String) {
    companion object {
        fun fromDb(row: Row) = Person(
                id = row.int("id"),
                name = row.string("name")
        )
    }
}

fun addPerson(request: Request): Response {
    val person = request.jsonBody<PersonRequest>() ?: return Response(Status.BAD_REQUEST)
    var newPerson: Person? = null
    using(dbSession()) { session ->
        session.transaction { transaction ->
            val id = transaction.updateAndReturnGeneratedKey(queryOf(
                    "INSERT INTO person(name) VALUES(?)",
                    person.name
            ))
            val personQuery = queryOf("SELECT * FROM person WHERE id = ?", id)
                    .map(Person.Companion::fromDb)
                    .asSingle
            newPerson = transaction.run(personQuery)
        }
    }
    return newPerson?.let {
        Response(Status.OK).jsonPojo(it)
    } ?: Response(Status.INTERNAL_SERVER_ERROR)
}

fun getPersons(request: Request): Response {
    val persons = arrayListOf<Person>()
    using(dbSession()) { session ->
        val personQuery = queryOf("SELECT * FROM person")
                .map(Person.Companion::fromDb)
                .asList

        persons += session.run(personQuery)
    }

    return Response(Status.OK).jsonPojo(persons)
}

fun main(args: Array<String>) {
    HikariCP.default("jdbc:sqlite:sample.db", "", "")

    using(dbSession()) { sesssion ->
        sesssion.transaction { transaction ->
            transaction.execute(queryOf("drop table if exists person"))
            transaction.execute(queryOf("create table person (id integer primary key autoincrement, name string not null)"))
        }
    }

    val app: HttpHandler = routes(
            "/persons" bind Method.GET to ::getPersons,
            "/person/add" bind Method.POST to ::addPerson
    )

    val filteredApp = CachingFilters.Response.NoCache()
            .then(timingFilter)
            .then(sqlExceptionHandler)
            .then(app)

    filteredApp.asServer(Undertow())
            .start()
            .block()
}
