#!/usr/bin/env python
"""Nirvana Compiler

Usage:
   nirvana_compiler.py file <filename>
   nirvana_compiler.py test
   nirvana_compiler.py cli

Options:
   -h --help    Show this screen
"""
from docopt import docopt
import sys
import os
import argparse
import ply.lex as lex
import ply.yacc as yacc
from pprint import pprint

test_data = '''
State: "350"
 > EntryState: [st1]
 > Function: "process-state-ek1-mk2val"
 > MatchRe: "request_service.* request for service=\[([^\]]+)\] from (\S+)$"
 > Description: "Requets for service launch is received"
 > Extract service: (:service event)
 > Extract pgtxn: (:pgtxn event)
 > Extract host: (str/join "_" [(:pgtxn event) (get regexOut 1)])
 > Extract ttl: 10
 > Extract pgtype: "launchRequestTracker"
 > Extract startTime: (:pgtime event)
'''

class NVLexer(object):
    tokens = ( 'STATE','COLON','STRING','ENTRYSTATE','MATCHRE',
          'PRIMKEY', 'DESCRIPTION','EXTRACT', 'FUNCTION',
          'GT', 'LSQBKT', 'RSQBKT', 'QSTRING','FILETYPE',
          'EXTRACT_EXPRESSION', 'NUMBER', 'PLUGINNAME',
          'SUCCESS_SMART_MSG','FAIL_SMART_MSG','LEDD_STRING_DDER',
            )
    states = (
        ('extract','exclusive'),
    )
    t_ignore = ' \t\x0c'
    t_extract_ignore = ' \t\x0c'
    t_GT = r'\>'
    t_COLON = r'\:'
    t_LSQBKT = r'\['
    t_RSQBKT = r'\]'

    def __init__(self):
        self.lexer = lex.lex(module=self)

    def t_NEWLINE(self, t):
        r'\n+'
        t.lexer.lineno += t.value.count("\n")
        #return t

    def t_extract_COLON(self, t):
        r'\:'
        if (t.lexer.level == 0):
            return t

    def t_extract_LPARN(self, t):
        r'\('
        if (t.lexer.level == 0):
            t.lexer.extract_start = t.lexer.lexpos - 1
        t.lexer.level += 1


    def t_extract_RPARN(self, t):
        r'\)'
        t.lexer.level -= 1
        if t.lexer.level == 0:
            t.value = t.lexer.lexdata[t.lexer.extract_start:t.lexer.lexpos]
            t.type='EXTRACT_EXPRESSION'
            t.lexer.lineno += t.value.count('\n')
            t.lexer.begin('INITIAL')
            return t

    def t_FUNCTION(self, t):
        r'Function'
        return t

    def t_STATE(self, t):
        r'(?i)state'
        return t

    def t_ENTRYSTATE(self, t):
        r'EntryState'
        return t

    def t_MATCHRE(self, t):
        r'MatchRe'
        return t

    def t_PRIMKEY(self, t):
        r'primary_key'
        return t

    def t_DESCRIPTION(self, t):
        r'Description'
        return t

    def t_FILETYPE(self, t):
        r'INFO|WARN|ERROR'
        return t

    def t_PLUGINNAME(self, t):
        r'PluginName'
        return t

    def t_SUCCESS_SMART_MSG(self, t):
        r'SuccessSmartMessage'
        return t

    def t_FAIL_SMART_MSG(self, t):
        r'FailSmartMessage'
        return t

    def t_EXTRACT(self, t):
        r'Extract'
        t.lexer.begin('extract')
        t.lexer.level = 0
        return t

    def t_STRING(self, t):
        r'[a-zA-Z_][a-zA-Z0-9_]*'
        return t

    def t_extract_STRING(self, t):
        r'[a-zA-Z_][a-zA-Z0-9_]*'
        if t.lexer.level == 0:
            return t

    def t_QSTRING(self, t):
        r'\"[^\n]*\"'
        return t

    # Langle Exclaimation Dash Dash - Dash Dash Exclaimation Rangle
    def t_LEDD_STRING_DDER(self, t):
        r'<!--[^/n].*--!>'
        return t

    def t_NUMBER(self, t):
      r'[0-9]+'
      return t

    def t_extract_QSTRING(self, t):
        r'\"[^\n]*\"'
        if t.lexer.level == 0:
            t.lexer.begin('INITIAL')
            return t

    def t_extract_NUMBER(self, t):
        r'[0-9]+'
        if t.lexer.level == 0:
            t.lexer.begin('INITIAL')
            return t

    def t_extract_EXCLUDING_PARAN(self, t):
        r'[^()]+'

    def t_extract_error(self, t):
        print("Illegal character '%s'" % t.value[0])
        t.lexer.skip(1)

    def t_error(self, t):
        print("Illegal character '%s'" % t.value[0])
        t.lexer.skip(1)

    def tokenize(self, data):
        self.lexer.input(data)
        while True:
            tok = self.lexer.token()
            if tok:
                yield tok
            else:
                break

class NVParser(object):
    def __init__(self):
        self.lexer = NVLexer()
        self.tokens = self.lexer.tokens
        self.parser = yacc.yacc(module=self, write_tables=0, debug=False)

    def parse(self, data):
        return self.parser.parse(data, self.lexer.lexer, 0, 0, None)

    def dbg_print(self, p):
        for idx in range(0, len(p)):
            print("\tP[%d]=%s" % (idx, p[idx]))

    def p_plugin(self, p):
        '''plugin : pluginname success_smart_msg fail_smart_msg statelist
        '''
        self.dbg_print(p)
        if len(p) == 3:
          p[0] = p[1] + p[2]
        elif len(p) == 4:
          p[0] = p[1] + p[2] + p[3]
        elif len(p) == 5:
          p[0] = p[1] + p[2] + p[3] + p[4]
        self.dbg_print(p)

    def p_pluginname(self, p):
        '''pluginname : PLUGINNAME COLON QSTRING'''
        p[0] = [{'pluginname': p[3]}]

    def p_success_smart_msg(self, p):
        '''success_smart_msg : SUCCESS_SMART_MSG COLON FILETYPE COLON LEDD_STRING_DDER'''
        self.dbg_print(p)
        p[0] = [{'success_smart_msg': p[5], 'success_file_type': p[3]}]

    def p_fail_smart_msg(self, p):
        '''fail_smart_msg : FAIL_SMART_MSG COLON FILETYPE COLON LEDD_STRING_DDER'''
        p[0] = [{'fail_smart_msg': p[5], 'fail_file_type': p[3]}]

    def p_statelist(self, p):
      '''statelist : state statelist
       |
      '''
      #self.dbg_print(p)
      if (len(p) == 3):
          if p[2] and p[1]:
              p[0] = p[2] + [p[1]]
          elif p[2]:
              p[0] = p[2]
          elif p[1]:
              p[0] = [p[1]]

    def p_state(self, p):
      '''state : STATE COLON NUMBER subitems'''
      p[0] = {'name': p[3], 'config': p[4]}

    def p_subitems(self, p):
        '''subitems : subitem subitems
        |
        '''
        if len(p) == 3:
            if p[1] and p[2]:
                p[0] = p[2] + [p[1]]
            elif p[1]:
                p[0] = [p[1]]
            elif p[2]:
                p[0] = p[2]


    def p_subitem(self, p):
        '''subitem : entrystate
                   | function
                   | description
                   | matchre
                   | extract
                   | primkey
        '''
        p[0] = p[1]

    def p_entrystate(self, p):
        '''entrystate : GT ENTRYSTATE COLON LSQBKT trstatelist RSQBKT
        '''
        p[0] = {'entrystate': p[5]}

    def p_trstatelist(self, p):
        '''trstatelist : NUMBER trstatelist
        | NUMBER
        |
        '''
        if len(p) == 2:
            p[0] = p[1]
            print "*** p[0]:%s p[1]:%s" % (p[0], p[1])
        elif len(p) == 3:
            p[0] = p[1] + " " + p[2]

    def p_function(self, p):
        '''function : GT FUNCTION COLON QSTRING'''
        p[0] = {'function': p[4]}

    def p_matchre(self, p):
        '''matchre : GT MATCHRE COLON QSTRING'''
        p[0] = {'matchre': p[4]}

    def p_primkey(self, p):
        '''primkey : GT PRIMKEY COLON QSTRING'''
        p[0] = {'primkey': p[4]}

    def p_description(self, p):
        '''description : GT DESCRIPTION COLON QSTRING
        '''
        p[0] = {'description' : p[4]}

    def p_extract(self, p):
      '''extract : GT EXTRACT STRING COLON EXTRACT_EXPRESSION
      '''
      p[0] = {'Extract': {'FieldName': p[3], 'cmd': p[5]}}

    def p_extract_str(self, p):
      '''extract : GT EXTRACT STRING COLON QSTRING
      '''
      p[0] = {'Extract': {'FieldName': p[3], 'value': p[5]}}

    def p_extract_num(self, p):
      '''extract : GT EXTRACT STRING COLON NUMBER
      '''
      p[0] = {'Extract': {'FieldName': p[3], 'value': int(p[5])}}

    def p_error(self, p):
        #print("Syntax error at line:%s TEXT:'%s'" % (p.lineno, p.value))
        print("Syntax error type:%s, value:%s line:%s, lexpos:%s" % (p.type, p.value, p.lineno, p.lexpos))
        pprint("    %s" % repr(p))
        pprint(p)

#arguments = docopt(__doc__);
#nvp = NVParser()
#if arguments['test']:
    # lexer.input(test_data)
    #print("PARSING THE DATA")
    #r = yacc.parse(test_data, tracking=True)
#    r = nvp.parse(test_data)
#    pprint("Parsed output from YACC")
#    pprint(r)
#elif arguments['cli']:
#    while 1:
#        try:
#            s = raw_input('Riemann > ')
#        except EOFError:
#            break
#        if not s: continue
#        pprint(nvp.parse(s))
#elif arguments['file']:
#    fname = arguments['<filename>']
#    print("Opening File : " + fname)
#    f = open(fname)
#    data = f.read()
#    f.close()
#    r = nvp.parse(data)
#    pprint("Parsed output from YACC")
#    pprint(r)

