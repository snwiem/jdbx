package com.datev.dbui;

import java.util.List;

/** The rows and columns that the database returns for one Statement. */
public record Result(List<String> columns, List<List<Object>> rows) {
}
