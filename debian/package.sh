#!/bin/sh -e
#$1 is the major-minor-version;
#ignore if tag already exists otherwise create a release commit and set the tag
( hg tags | grep -q "$1 " ) || ( echo $1 > debian/RELEASE && hg commit -m "release $1" --addremove debian/RELEASE && hg tag $1 )

#create a change-log file using the repository tags (only major versions)
hg log --style debian/log_pattern > debian/changelog

#get the last complete version from the created log file
MAJOR_MINOR_FIX=$(head -1 debian/changelog | sed  's/[^(]*(\([^)]*\)).*/\1/g')

#bz the whole source for the ppa, minus build and dist dirs
tar -cjf ../bookjar_${MAJOR_MINOR_FIX}.orig.tar.bz2 --exclude='*build' --exclude='*dist' --exclude-vcs *

# Note: your .gnupg/gpg.conf must have the no-tty and batch options,
# otherwise debuild will fail (because it will call gpg, which will
# try to open a tty). This happens running from a ide

debuild -kC81E6C93
sudo dpkg -i ../bookjar_${MAJOR_MINOR_FIX}_all.deb
#dput ppa:i30817/bookjar ../bookjar_${MAJOR_MINOR_FIX}_source.changes
rm ../bookjar_${MAJOR_MINOR_FIX}*