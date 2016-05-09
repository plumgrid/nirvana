import os
import subprocess
import argparse

parser = argparse.ArgumentParser(description='Optional app description')
# Optional argument
parser.add_argument('-f', '--fname', type=str, help='File to compile')
parser.add_argument('-a', '--all', action='store_true', help='Compile all .nrv files')

args = parser.parse_args()
compile_all = args.all
single_filename = args.fname
print "%s, %s" %(compile_all, single_filename)

if compile_all:
  for subdir, dirs, files in os.walk('../nrv_plugin'):
    for myfile in files:
      filename, file_extension = os.path.splitext(myfile)
      # Work only on files with extension ".nrv".
      if file_extension == ".nrv":
        #print "all"
        subprocess.call('python ../compiler/generate_code.py %s/%s --output ../plugins/%s.clj' % (subdir, myfile, filename), shell=True)
elif single_filename is not None:
  filename, file_extension = os.path.splitext(single_filename)
  if file_extension == ".nrv":
    subprocess.call('python ../compiler/generate_code.py %s --output ../plugins/%s.clj' % (single_filename, filename), shell=True)
  else:
    print "Unable to compile file having extension other than '.nrv'"
else:
  print "Wrong/incomplete arguments. Please use 'python build.py -h' for help."
