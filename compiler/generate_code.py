import nrv_compiler
import sys
import os
from pprint import pprint
from os.path import basename

def write_header(plugin_name, file_handle):
  string = "(include \"../pg_utils.clj\")" + os.linesep
  string += "(ns plugins." + plugin_name + os.linesep
  string += "  \"" + plugin_name + "\"" + os.linesep
  string += "  (:require [riemann.config :refer :all]" + os.linesep
  string += "            [clojure.string :as str]" + os.linesep
  string += "            [riemann.streams :refer :all]))" + os.linesep
  string += "(defn " + plugin_name + " []" + os.linesep
  string += "  (fn [e]" + os.linesep
  file_handle.write(string)

def get_file_data(file_name):
  f = open(fname)
  data = f.read()
  f.close()
  return data

def write_state(fun_name, states, regex, description, prim_keys, extract_fields, file_handle):
  string = "    (" + fun_name + os.linesep
  string += "      " + states + os.linesep
  string += "      #" + regex + os.linesep
  string += "      " + description + os.linesep
  for prim_key in prim_keys:
    string += "      " + prim_key + os.linesep

  string += "      " + "(fn [strm event regex-match]" + os.linesep
  string += "        " + "(assoc strm" + os.linesep
  for extact_field in extract_fields:
    string += "          " + extact_field + os.linesep

  string += "          )" + os.linesep
  string += "       )" + os.linesep
  string += "    )" + os.linesep

  file_handle.write(string)

def write_last_state(fun_name, states, regex, description, prim_keys, smart_msg, file_handle):
  string = "    (" + fun_name + os.linesep
  string += "      " + states + os.linesep
  string += "      #" + regex + os.linesep
  string += "      " + description + os.linesep
  for prim_key in prim_keys:
    string += "      " + prim_key + os.linesep

  string += "      (fn [strm event regex-match]" + os.linesep
  string += "        (do" + os.linesep
  string += "          (def concise-msg (apply str (now) " + smart_msg + os.linesep
  string += "              )" + os.linesep # apply
  string += "            )" + os.linesep # def
  string += "          )" + os.linesep # do
  string += "        (file-write info-log-location [concise-msg \"\\n\"])" + os.linesep
  string += "        \"DELETE\"" + os.linesep
  string += "        )" + os.linesep # fn
  string += "      )" + os.linesep # function
  string += "    )" + os.linesep # top fn
  string += "  )" + os.linesep # top defn

  file_handle.write(string)

def write_error_smart_msg(plugin_name, msg, file_handle):
  string = "(defn " + plugin_name + "-expired [& children]" + os.linesep
  string += "  (fn [strm]" + os.linesep
  string += "    (let [concise-msg (apply str (now) " + msg + ")]" + os.linesep
  string += "      (file-write error-log-location [concise-msg \"\\n\"])" + os.linesep
  string += "      )" + os.linesep
  string += "    )" + os.linesep
  string += "  )" + os.linesep
  file_handle.write(string)

print ('Argument List:', str(sys.argv))
nvp = nrv_compiler.NVParser()
fname = sys.argv[1]
# TODO: Make sure that extension of file is nrv.
data = get_file_data(fname)
r = nvp.parse(data)
pprint("Parsed output from YACC")
pprint(r)

# Get name of file without extension.
output_fname = os.path.splitext(fname)[0] + ".clj"
target = open(output_fname, 'w')

# Extract values from parser
plugin_name = r[0]['pluginname'][1:-1]
smart_msg = r[1]['smart_msg'][5:-5]
error_smart_msg = r[2]['error_smart_msg'][5:-5]
states_list = r[3:]

# Write includes/headers which are (almost) common among plugins.
write_header(plugin_name, target)

i = 1
len_states_list = len(states_list)
primkey_lst = list()
extract_lst = list()

for states in reversed(states_list):
  primkey_lst = list()
  extract_lst = list()

  config_list = states['config']
  for item in reversed(config_list):
    if 'function' in item:
      fun_name = item['function'][1:-1]
    elif 'matchre' in item:
      regex = item['matchre']
    elif 'description' in item:
      description = item['description']
    elif 'primkey' in item:
      primkey_lst.append(item['primkey'])
    elif 'entrystate' in item:
      str_entrystate = states['name'] + " " + str(item['entrystate']) + " e"
    elif 'Extract' in item:
      if 'cmd' in item['Extract']:
        extract_field = ":" + item['Extract']['FieldName'] + " " + item['Extract']['cmd']
      else:
        extract_field = ":" + item['Extract']['FieldName'] + " " + str(item['Extract']['value'])
      extract_lst.append(extract_field)

  if i < len_states_list:
    write_state(fun_name, str_entrystate, regex, description, primkey_lst, extract_lst, target)
  else:
    write_last_state(fun_name, str_entrystate, regex, description, primkey_lst, smart_msg,  target)
  i += 1

write_error_smart_msg(plugin_name, error_smart_msg, target)
