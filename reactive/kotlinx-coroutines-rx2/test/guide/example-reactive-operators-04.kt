/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

// This file was automatically generated from coroutines-guide-reactive.md by Knit tool. Do not edit.
package kotlinx.coroutines.experimental.rx2.guide.operators04

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.reactive.*
import org.reactivestreams.*
import kotlin.coroutines.experimental.*

fun <T> Publisher<Publisher<T>>.merge(context: CoroutineContext) = publish<T>(context) {
  consumeEach { pub ->                 // for each publisher received on the source channel
      launch(coroutineContext) {       // launch a child coroutine
          pub.consumeEach { send(it) } // resend all element from this publisher
      }
  }
}

fun rangeWithInterval(context: CoroutineContext, time: Long, start: Int, count: Int) = publish<Int>(context) {
    for (x in start until start + count) { 
        delay(time) // wait before sending each number
        send(x)
    }
}

fun testPub(context: CoroutineContext) = publish<Publisher<Int>>(context) {
    send(rangeWithInterval(context, 250, 1, 4)) // number 1 at 250ms, 2 at 500ms, 3 at 750ms, 4 at 1000ms 
    delay(100) // wait for 100 ms
    send(rangeWithInterval(context, 500, 11, 3)) // number 11 at 600ms, 12 at 1100ms, 13 at 1600ms
    delay(1100) // wait for 1.1s - done in 1.2 sec after start
}

fun main(args: Array<String>) = runBlocking<Unit> {
    testPub(coroutineContext).merge(coroutineContext).consumeEach { println(it) } // print the whole stream
}
