#!/bin/sh -e

#This is a upload script only. 
#The project file builds the project locally and then uploads it to the ppa. 
#No building is done remotely (because it would be massively inconvenient)
#Needs git, tar, debuild, dput + project build deps installed.

#use this commit message metalanguage in the hope i can use the 
#project in the future (it has no debian template yet): 
# https://github.com/vaab/gitchangelog/blob/master/gitchangelog.rc.reference

#$1 is the major-minor version;
#ignore if tag already exists otherwise create and push it
( git tag | grep -Fxq "$1" ) || ( git tag -a $1 -m "release $1"; git push origin $1 )


#TODO use gitchangelog when it can generate debian/changelogs
LAST_TAG=$(git describe --abbrev=0 --tags)
COMMITS=$(git log ${LAST_TAG}..HEAD --no-merges --pretty=format:'  * %s')
PATCH=`echo "$COMMITS" | wc -l`
CURRENT="${LAST_TAG}.${PATCH}"
DISTRO=$(lsb_release -cs)
RELEASE_DATE=$(date --rfc-2822)
echo "bookjar (${CURRENT}) ${DISTRO}; urgency=low\n${COMMITS}\n -- i30817 (launchpad key) <i30817@gmail.com>  ${RELEASE_DATE}" > debian/changelog

#bz the whole source for the ppa, minus build and dist dirs
tar -cjf ../bookjar_${CURRENT}.orig.tar.bz2 --exclude='*build' --exclude='*dist' --exclude-vcs *

# Note: your .gnupg/gpg.conf must have the no-tty and batch options,
# otherwise debuild will fail (because it will call gpg, which will
# try to open a tty). This happens running from a ide

#this is not the real key but a key id. It will ask for the real key to verify
debuild -S -kC81E6C93
dput ppa:i30817/bookjar ../bookjar_${CURRENT}_source.changes
rm ../bookjar_${CURRENT}*
