package me.tsukanov.counter.repository

import me.tsukanov.counter.domain.CounterEvent
import me.tsukanov.counter.repository.exceptions.MissingCounterException
import me.tsukanov.counter.repository.exceptions.UnsupportedExportVersionException
import java.io.IOException

interface CounterStorage<T> {
    /**
     * Retrieves all stored counters.
     *
     * @param addDefault Whether default counter needs to be added if no counters are stored.
     */
    fun readAll(addDefault: Boolean): List<T>

    /** Retrieves counter with a specified identifier.  */
    @Throws(MissingCounterException::class)
    fun read(counterIdentifier: Any): T

    /** Retrieves first encountered counter. If one doesn't exists, creates a default one.  */
    fun first(): T?

    /**
     * Saves provided counter in storage. If it's identifier is already defined, existing counter will
     * be overwritten. The provided [event] is appended to the counter's change history.
     */
    fun write(counter: T, event: CounterEvent)

    /**
     * Writes provided counters into storage.
     *
     *
     * **This will overwrite all existing counters.**
     */
    fun overwriteAll(counters: List<T>)

    /** Deletes counter with a specified identifier.  */
    fun delete(counterIdentifier: Any)

    /** Removes all stored counters.  */
    fun wipe()

    /** Exports all counters into CSV formatted string.  */
    @Throws(IOException::class)
    fun toCsv(): String

    /**
     * Imports counters and their history from a previously produced CSV export.
     *
     * Replaces the current storage contents on success.
     */
    @Throws(IOException::class, UnsupportedExportVersionException::class)
    fun fromCsv(content: String)
}
