import nrv_compiler
import sys
import os
import argparse
from pprint import pprint
from os.path import basename

# Write includes.
def write_header(plugin_name, file_handle):
  string = "(include \"../pg_utils.clj\")" + os.linesep
  string = "(include \"./start_states_consts.clj\")" + os.linesep
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
  for prim_key in reversed(prim_keys):
    string += "      " + prim_key + os.linesep

  string += "      " + "(fn [strm event regex-match]" + os.linesep
  string += "        " + "(assoc strm" + os.linesep
  for extact_field in extract_fields:
    string += "          " + extact_field + os.linesep

  string += "          )" + os.linesep
  string += "       )" + os.linesep
  string += "    )" + os.linesep

  file_handle.write(string)

def write_last_state(fun_name, states, regex, description, prim_keys, success_smart_msg, success_file_type, file_handle):
  string = "    (" + fun_name + os.linesep
  string += "      " + states + os.linesep
  string += "      #" + regex + os.linesep
  string += "      " + description + os.linesep
  for prim_key in reversed(prim_keys):
    string += "      " + prim_key + os.linesep

  string += "      (fn [strm event regex-match]" + os.linesep
  string += "        (do" + os.linesep
  string += "          (def concise-msg (apply str (now) \" : \"" + success_smart_msg + os.linesep
  string += "              )" + os.linesep # apply
  string += "            )" + os.linesep # def
  string += "          )" + os.linesep # do
  string += "        (file-write " + success_file_type + "-log-location [concise-msg \"\\n\"])" + os.linesep
  string += "        \"DELETE\"" + os.linesep
  string += "        )" + os.linesep # fn
  string += "      )" + os.linesep # function
  string += "    )" + os.linesep # top fn
  string += "  )" + os.linesep # top defn

  file_handle.write(string)

def write_fail_smart_msg(plugin_name, fail_smart_msg, fail_file_type, file_handle):
  string = "(defn " + plugin_name + "_expired [& children]" + os.linesep
  string += "  (fn [strm]" + os.linesep
  string += "    (let [concise-msg (apply str (now) \" : \"" + fail_smart_msg + ")]" + os.linesep
  string += "      (file-write " + fail_file_type + "-log-location [concise-msg \"\\n\"])" + os.linesep
  string += "      )" + os.linesep
  string += "    )" + os.linesep
  string += "  )" + os.linesep
  file_handle.write(string)

# *********************** Parse arguments *******************************
parser = argparse.ArgumentParser(description='Optional app description')
# Required positional argument
parser.add_argument('fname', type=str, help='Name of .nrv file')
# Optional argument
parser.add_argument('--output', type=str, help='Path to store compiler output file')
args = parser.parse_args()
print("Name of file %s " % args.fname)
print ("Name of output file %s" % args.output)
fname = args.fname
output_fname = args.output
if output_fname is None:
  output_fname = os.path.splitext(fname)[0] + ".clj"

nvp = nrv_compiler.NVParser()
# TODO: Make sure that extension of file is nrv.
data = get_file_data(fname)
r = nvp.parse(data)
pprint("Parsed output from YACC")
pprint(r)

# Get name of file without extension.
target = open(output_fname, 'w')

# Extract values from parser
plugin_name = r[0]['pluginname'][1:-1]
success_smart_msg = r[1]['success_smart_msg'][5:-5]
success_file_type = r[1]['success_file_type'].lower()
fail_smart_msg = r[2]['fail_smart_msg'][5:-5]
fail_file_type = r[2]['fail_file_type'].lower()
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
      if str(item['entrystate']) == "None":
        str_entrystate = states['name'] + " [] e"
      else:
        str_entrystate = states['name'] + " [" + str(item['entrystate']) + "] e"
    elif 'Extract' in item:
      if 'cmd' in item['Extract']:
        extract_field = ":" + item['Extract']['FieldName'] + " " + item['Extract']['cmd']
      else:
        extract_field = ":" + item['Extract']['FieldName'] + " " + str(item['Extract']['value'])
      extract_lst.append(extract_field)

  if i < len_states_list:
    write_state(fun_name, str_entrystate, regex, description, primkey_lst, extract_lst, target)
  else:
    write_last_state(fun_name, str_entrystate, regex, description, primkey_lst, success_smart_msg, success_file_type, target)
  i += 1

write_fail_smart_msg(plugin_name, fail_smart_msg, fail_file_type, target)
