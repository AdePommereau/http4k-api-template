package com.github.wmxd

import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.http4k.lens.LensFailure

inline fun <reified T : Any> Response.jsonPojo(pojo: T) = this.with(
        Body.auto<T>().toLens() of pojo
)

inline fun <reified T : Any> Request.jsonBody(): T? = try {
    Body.auto<T>().toLens().extract(this)
} catch (e: LensFailure) {
    e.printStackTrace()
    null
}