package me.tsukanov.counter.repository.exceptions

class UnsupportedExportVersionException(val foundVersion: Int?, val supportedVersion: Int) :
    Exception(
        if (foundVersion == null) {
            "Export is missing a version header; expected version $supportedVersion."
        } else {
            "Export version $foundVersion is not supported (this build supports version $supportedVersion)."
        }
    )
