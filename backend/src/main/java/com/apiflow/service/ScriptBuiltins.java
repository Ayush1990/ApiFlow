package com.apiflow.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ScriptBuiltins {

	private ScriptBuiltins() {
	}

	public static Map<String, String> modules() {
		Map<String, String> modules = new LinkedHashMap<>();
		modules.put("lodash", LODASH);
		modules.put("_", LODASH);
		modules.put("moment", MOMENT);
		return modules;
	}

	private static final String LODASH = """
			var _ = {
			  get: function(obj, path, def) {
			    if (obj == null) return def;
			    var parts = String(path).split('.');
			    var cur = obj;
			    for (var i = 0; i < parts.length; i++) {
			      if (cur == null || cur[parts[i]] === undefined) return def;
			      cur = cur[parts[i]];
			    }
			    return cur;
			  },
			  map: function(arr, fn) { return (arr || []).map(fn); },
			  filter: function(arr, fn) { return (arr || []).filter(fn); },
			  find: function(arr, fn) { return (arr || []).find(fn); },
			  includes: function(arr, value) { return (arr || []).includes(value); },
			  merge: function() {
			    var out = {};
			    for (var i = 0; i < arguments.length; i++) {
			      var src = arguments[i] || {};
			      for (var key in src) out[key] = src[key];
			    }
			    return out;
			  },
			  cloneDeep: function(value) { return JSON.parse(JSON.stringify(value)); },
			  isEmpty: function(value) {
			    if (value == null) return true;
			    if (Array.isArray(value) || typeof value === 'string') return value.length === 0;
			    return Object.keys(value).length === 0;
			  },
			  uniq: function(arr) { return Array.from(new Set(arr || [])); },
			  chunk: function(arr, size) {
			    var out = [];
			    for (var i = 0; i < (arr || []).length; i += size) out.push(arr.slice(i, i + size));
			    return out;
			  }
			};
			module.exports = _;
			""";

	private static final String MOMENT = """
			function moment(input) {
			  var d = input ? new Date(input) : new Date();
			  function pad(n) { return String(n).padStart(2, '0'); }
			  return {
			    format: function(fmt) {
			      if (fmt === 'ISO') return d.toISOString();
			      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
			    },
			    add: function(amount, unit) {
			      var copy = new Date(d.getTime());
			      if (unit === 'days') copy.setDate(copy.getDate() + amount);
			      else if (unit === 'hours') copy.setHours(copy.getHours() + amount);
			      else if (unit === 'minutes') copy.setMinutes(copy.getMinutes() + amount);
			      else copy.setTime(copy.getTime() + amount);
			      return moment(copy);
			    },
			    unix: function() { return Math.floor(d.getTime() / 1000); },
			    valueOf: function() { return d.getTime(); },
			    toISOString: function() { return d.toISOString(); }
			  };
			}
			moment.utc = function(input) { return moment(input); };
			module.exports = moment;
			""";

}
