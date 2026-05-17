package me.tsukanov.counter.domain

enum class CounterEvent {
    CREATED,
    INCREMENT,
    DECREMENT,
    RESET,
    EDITED,
    DELETED
}
