package me.tsukanov.counter.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import me.tsukanov.counter.domain.CounterEvent
import me.tsukanov.counter.domain.IntegerCounter
import me.tsukanov.counter.domain.exception.CounterException
import me.tsukanov.counter.infrastructure.Actions
import me.tsukanov.counter.infrastructure.BroadcastHelper
import me.tsukanov.counter.repository.exceptions.MissingCounterException
import me.tsukanov.counter.repository.exceptions.UnsupportedExportVersionException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.StringReader
import java.util.LinkedList

/**
 * Counter storage that uses [SharedPreferences] as a medium.
 *
 *
 * This implementation us based on the [IntegerCounter] variation of the [Counter].
 */
class SharedPrefsCounterStorage(
    context: Context,
    private val broadcastHelper: BroadcastHelper,
    private val defaultCounterName: String
) : CounterStorage<IntegerCounter> {

    private val values: SharedPreferences =
        context.getSharedPreferences(VALUES_FILE_NAME, Context.MODE_PRIVATE)
    private val updateTimestamps: SharedPreferences =
        context.getSharedPreferences(UPDATE_TIMESTAMPS_FILE_NAME, Context.MODE_PRIVATE)
    private val updateHistory: SharedPreferences =
        context.getSharedPreferences(HISTORY_FILE_NAME, Context.MODE_PRIVATE)

    override fun readAll(addDefault: Boolean): List<IntegerCounter> {
        val counters: MutableList<IntegerCounter> = LinkedList()

        val valuesMap = values.all

        try {
            if (valuesMap.isEmpty() && addDefault) {
                val defaultCounter = IntegerCounter(this.defaultCounterName)
                counters.add(defaultCounter)
                write(defaultCounter, CounterEvent.CREATED)
                return counters
            }

            for ((key, value) in valuesMap) {
                val updateTimestampStr = updateTimestamps.getString(key, null)
                val updateTimestamp =
                    if (updateTimestampStr != null)
                        DateTime.parse(updateTimestampStr, TIMESTAMP_FORMATTER)
                    else
                        null

                bootstrapHistoryIfMissing(key, (value as Int?)!!, updateTimestamp)

                counters.add(
                    IntegerCounter(key, value, updateTimestamp)
                )
            }
            counters.sortWith { x: IntegerCounter, y: IntegerCounter -> x.name.compareTo(y.name) }
            return counters
        } catch (e: CounterException) {
            throw RuntimeException(e)
        }
    }

    @Throws(MissingCounterException::class)
    override fun read(counterIdentifier: Any): IntegerCounter {
        val name = counterIdentifier.toString()
        val counters = readAll(false)

        for (c in counters) {
            if (c.name == name) {
                return c
            }
        }

        throw MissingCounterException(String.format("Unable find counter: %s", name))
    }

    override fun first(): IntegerCounter {
        return readAll(true)[0]
    }

    /**
     * Saves provided counter in storage. If it's identifier is already defined, existing counter will
     * be overwritten. The [event] is appended to the counter's change history.
     */
    @SuppressLint("ApplySharedPref")
    override fun write(counter: IntegerCounter, event: CounterEvent) {
        values.edit().putInt(counter.name, counter.value).commit()

        val timestamp = counter.lastUpdatedDate ?: DateTime()
        if (counter.lastUpdatedDate != null) {
            updateTimestamps
                .edit()
                .putString(counter.name, counter.lastUpdatedDate!!.toString(TIMESTAMP_FORMATTER))
                .commit()
        }

        appendHistoryEvent(counter.name, event, counter.value, timestamp)

        broadcastHelper.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @SuppressLint("ApplySharedPref")
    override fun overwriteAll(counters: List<IntegerCounter>) {
        Log.i(TAG, String.format("Writing %s counters to storage", counters.size))

        val prefsEditor = values.edit()
        prefsEditor.clear()
        for (c in counters) {
            prefsEditor.putInt(c.name, c.value)
        }
        val success = prefsEditor.commit()

        if (success) {
            Log.i(TAG, "Writing has been completed")
        } else {
            Log.e(TAG, "Failed to overwrite counters to storage")
        }

        val historyEditor = updateHistory.edit()
        historyEditor.clear()
        for (c in counters) {
            val timestamp = c.lastUpdatedDate ?: DateTime()
            val arr = JSONArray()
            arr.put(buildHistoryEntry(CounterEvent.CREATED, c.value, timestamp))
            historyEditor.putString(c.name, arr.toString())
        }
        historyEditor.commit()

        broadcastHelper.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @SuppressLint("ApplySharedPref")
    override fun delete(counterIdentifier: Any) {
        val name = counterIdentifier.toString()
        val lastValue = values.getInt(name, 0)
        appendHistoryEvent(name, CounterEvent.DELETED, lastValue, DateTime())

        values.edit().remove(name).commit()
        updateTimestamps.edit().remove(name).commit()

        broadcastHelper.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @SuppressLint("ApplySharedPref")
    override fun wipe() {
        values.edit().clear().commit()
        updateTimestamps.edit().clear().commit()
        updateHistory.edit().clear().commit()

        broadcastHelper.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    @Throws(IOException::class)
    override fun toCsv(): String {
        val output = StringBuilder()
        output.append("# CounterFork Export\n")
        output.append("# Version: ").append(EXPORT_FORMAT_VERSION).append('\n')
        output.append("# ExportedAt: ")
            .append(DateTime.now().toString(TIMESTAMP_FORMATTER)).append('\n')
        output.append('\n')

        val format = CSVFormat.POSTGRESQL_CSV
        val counters = readAll(false).sortedBy { it.name }

        for (counter in counters) {
            output.append(SECTION_COUNTER).append('\n')
            CSVPrinter(output, format).use { csvPrinter ->
                csvPrinter.printRecord(HEADER_COUNTER_NAME, HEADER_COUNTER_VALUE, HEADER_COUNTER_LAST_UPDATE)
                csvPrinter.printRecord(
                    counter.name,
                    counter.value,
                    counter.lastUpdatedDate?.toString(TIMESTAMP_FORMATTER) ?: ""
                )
            }
            output.append('\n')

            output.append(SECTION_HISTORY).append('\n')
            CSVPrinter(output, format).use { csvPrinter ->
                csvPrinter.printRecord(HEADER_HISTORY_EVENT, HEADER_HISTORY_VALUE, HEADER_HISTORY_TIMESTAMP)
                val history = readHistory(counter.name)
                for (i in 0 until history.length()) {
                    val entry = history.getJSONObject(i)
                    csvPrinter.printRecord(
                        entry.getString(JSON_KEY_TYPE),
                        entry.getInt(JSON_KEY_VALUE),
                        entry.getString(JSON_KEY_TIMESTAMP)
                    )
                }
            }
            output.append('\n')
        }

        return output.toString()
    }

    @SuppressLint("ApplySharedPref")
    @Throws(IOException::class, UnsupportedExportVersionException::class)
    override fun fromCsv(content: String) {
        val version = parseVersion(content)
        if (version != EXPORT_FORMAT_VERSION) {
            throw UnsupportedExportVersionException(version, EXPORT_FORMAT_VERSION)
        }

        val parsed = parseBlocks(content)

        values.edit().clear().commit()
        updateTimestamps.edit().clear().commit()
        updateHistory.edit().clear().commit()

        for (entry in parsed) {
            values.edit().putInt(entry.name, entry.value).commit()
            if (entry.lastUpdate != null) {
                updateTimestamps.edit()
                    .putString(entry.name, entry.lastUpdate.toString(TIMESTAMP_FORMATTER))
                    .commit()
            }
            updateHistory.edit().putString(entry.name, entry.history.toString()).commit()
        }

        broadcastHelper.sendBroadcast(Actions.COUNTER_SET_CHANGE)
    }

    private fun readHistory(name: String): JSONArray {
        val raw = updateHistory.getString(name, null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Corrupt history for counter '$name'; resetting.", e)
            JSONArray()
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun appendHistoryEvent(
        name: String,
        event: CounterEvent,
        value: Int,
        timestamp: DateTime
    ) {
        val history = readHistory(name)
        history.put(buildHistoryEntry(event, value, timestamp))
        updateHistory.edit().putString(name, history.toString()).commit()
    }

    private fun buildHistoryEntry(event: CounterEvent, value: Int, timestamp: DateTime): JSONObject {
        return JSONObject().apply {
            put(JSON_KEY_TYPE, event.name)
            put(JSON_KEY_VALUE, value)
            put(JSON_KEY_TIMESTAMP, timestamp.toString(TIMESTAMP_FORMATTER))
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun bootstrapHistoryIfMissing(name: String, value: Int, timestamp: DateTime?) {
        if (updateHistory.contains(name)) return
        val arr = JSONArray()
        arr.put(buildHistoryEntry(CounterEvent.CREATED, value, timestamp ?: DateTime.now()))
        updateHistory.edit().putString(name, arr.toString()).commit()
    }

    private data class ImportedCounter(
        val name: String,
        val value: Int,
        val lastUpdate: DateTime?,
        val history: JSONArray
    )

    private fun parseVersion(content: String): Int? {
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith("#")) {
                if (line.startsWith(SECTION_COUNTER) || line.startsWith(SECTION_HISTORY)) return null
                continue
            }
            val payload = line.removePrefix("#").trim()
            if (payload.startsWith("Version:", ignoreCase = true)) {
                return payload.substringAfter(':').trim().toIntOrNull()
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun parseBlocks(content: String): List<ImportedCounter> {
        val result = mutableListOf<ImportedCounter>()
        val lines = content.lines()
        var idx = 0

        while (idx < lines.size) {
            val line = lines[idx].trim()
            if (line != SECTION_COUNTER) {
                idx++
                continue
            }
            idx++

            val counterBlock = collectBlockUntilNextSection(lines, idx)
            idx = counterBlock.endIndex
            val counterRecord = parseSingleRecord(counterBlock.body) ?: continue

            val name = counterRecord[HEADER_COUNTER_NAME]
                ?: throw IOException("Counter block missing Name column")
            val valueStr = counterRecord[HEADER_COUNTER_VALUE]
                ?: throw IOException("Counter block missing Value column")
            val value = valueStr.toIntOrNull()
                ?: throw IOException("Counter '$name' has non-integer value '$valueStr'")
            val lastUpdateStr = counterRecord[HEADER_COUNTER_LAST_UPDATE]
            val lastUpdate = if (lastUpdateStr.isNullOrEmpty()) null
            else DateTime.parse(lastUpdateStr, TIMESTAMP_FORMATTER)

            val history = JSONArray()
            if (idx < lines.size && lines[idx].trim() == SECTION_HISTORY) {
                idx++
                val historyBlock = collectBlockUntilNextSection(lines, idx)
                idx = historyBlock.endIndex
                for (record in parseAllRecords(historyBlock.body)) {
                    val type = record[HEADER_HISTORY_EVENT]
                        ?: throw IOException("History entry for '$name' missing Event column")
                    val hValueStr = record[HEADER_HISTORY_VALUE]
                        ?: throw IOException("History entry for '$name' missing Value column")
                    val hValue = hValueStr.toIntOrNull()
                        ?: throw IOException("History entry for '$name' has non-integer value '$hValueStr'")
                    val hTimestamp = record[HEADER_HISTORY_TIMESTAMP]
                        ?: throw IOException("History entry for '$name' missing Timestamp column")
                    history.put(JSONObject().apply {
                        put(JSON_KEY_TYPE, type)
                        put(JSON_KEY_VALUE, hValue)
                        put(JSON_KEY_TIMESTAMP, hTimestamp)
                    })
                }
            }

            result.add(ImportedCounter(name, value, lastUpdate, history))
        }

        return result
    }

    private data class Block(val body: String, val endIndex: Int)

    private fun collectBlockUntilNextSection(lines: List<String>, start: Int): Block {
        val sb = StringBuilder()
        var i = start
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed == SECTION_COUNTER || trimmed == SECTION_HISTORY) break
            sb.append(lines[i]).append('\n')
            i++
        }
        return Block(sb.toString(), i)
    }

    @Throws(IOException::class)
    private fun parseSingleRecord(body: String): Map<String, String>? {
        val records = parseAllRecords(body)
        return records.firstOrNull()
    }

    @Throws(IOException::class)
    private fun parseAllRecords(body: String): List<Map<String, String>> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val parser = CSVParser(
            StringReader(trimmed),
            CSVFormat.POSTGRESQL_CSV.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setCommentMarker('#')
                .setIgnoreEmptyLines(true)
                .build()
        )
        parser.use {
            return it.records.map { record -> record.toMap() }
        }
    }

    companion object {
        private val TAG: String = SharedPrefsCounterStorage::class.java.simpleName

        const val EXPORT_FORMAT_VERSION: Int = 2

        private const val VALUES_FILE_NAME = "counters"
        private const val UPDATE_TIMESTAMPS_FILE_NAME = "update-timestamps"
        private const val HISTORY_FILE_NAME = "update-history"

        private const val SECTION_COUNTER = "[Counter]"
        private const val SECTION_HISTORY = "[History]"

        private const val HEADER_COUNTER_NAME = "Name"
        private const val HEADER_COUNTER_VALUE = "Value"
        private const val HEADER_COUNTER_LAST_UPDATE = "LastUpdate"
        private const val HEADER_HISTORY_EVENT = "Event"
        private const val HEADER_HISTORY_VALUE = "Value"
        private const val HEADER_HISTORY_TIMESTAMP = "Timestamp"

        private const val JSON_KEY_TYPE = "type"
        private const val JSON_KEY_VALUE = "value"
        private const val JSON_KEY_TIMESTAMP = "timestamp"

        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            ISODateTimeFormat.basicDateTimeNoMillis().withZone(DateTimeZone.getDefault())
    }
}
