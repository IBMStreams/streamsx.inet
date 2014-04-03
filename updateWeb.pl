#!/usr/bin/perl

use strict;
use Getopt::Long;


my $debug = 0;
my $dryrun = 0;
my $doPush;
my $doCommit=1; 
my $message = "Update documents";
my $help;
my $useTemp;

GetOptions('push!'=>\$doPush,
	   'commit!'=>\$doCommit,
	   'dryrun' => \$dryrun,
	   'message|m=s' => \$message,
           'help|h|?'=>\$help,
           'createTempRepository' => \$useTemp);
if ($dryrun) {
    $doPush = 0;
    $doCommit = 0;
}

if ($doPush && !$doCommit) {
    print STDERR "If --push is specified, then commit must be enabled\n";
} 

if ($help) {
    print STDOUT "makeDocs.pl runs spl-make-doc in the current repository for all toolkits and samples,\n";
    print STDOUT "and then commits the doc updates to a second document repository (one set to gh-pages).\n";
    print STDOUT "Usage: makeDocs.pl [--commit|--nocommit] [--push|--nopush] [--message <msg>] <gh-pagesrepos>\n";
    exit 0;
}
if (scalar @ARGV != 1 && !$useTemp) {
    print STDERR "Usage: makeDocs.pl <pagesrepo>\n";
    exit 1;
}
if ($useTemp) {
    $doCommit or die "Must do a commit if making a temporary repository";
    $doPush or die "Must do a push if using a temporary repository";
}
my $pagesLocation = $ARGV[0];
die "$pagesLocation not a valid directory" unless $useTemp || (-e $pagesLocation && -d $pagesLocation);


sub quotedollar($) {
    my ($instring) = @_;
    $instring =~ s/\$/\\\$/g;
    return $instring;
}

sub run(@) {
    my @cmd = @_;

    if ($dryrun) {
	print "@cmd\n";
    }
    else {
	system(@cmd);
    }
}

sub runString($) {
    my ($cmd) = @_;

    if ($dryrun) {
	print "$cmd\n";
    }
    else {
	system($cmd);
    }
    return ($? >> 8);
}

sub lookForApp($$) {
    my ($dir,$exclude) = @_;
    $debug && print "lookForApp($dir,$exclude)\n";
    my @files = `ls $dir`;
    my $changes = 0;
    for my $f (@files) {
	chomp $f;
	my $fullName = "$dir/$f";
	$debug && print "Trying file $fullName\n";
	next if (defined $exclude && $dir =~ /$exclude/);
	next unless (-d "$fullName");
	if (-e "$fullName/doc/spldoc") {
	    $debug && print "directory $fullName appears to have a documentation\n";
	    if ($dryrun) {
		print "Would copy $fullName/doc to $pagesLocation\n";
	    }
	    else {
		system("cp -r $fullName/doc $pagesLocation/$fullName");
		system("cd $pagesLocation; git add -A $fullName/doc");
	    }
	    $? >> 8 == 0 or die "Problem adding.";
	}
	else {
	    $debug && print "no docs found in $fullName\n";
	    lookForApp("$fullName");
	}
    }
}

sub main() {
    system("ant spldoc");
    $? >> 8 == 0 or die "Could not build spl doc";
    # Make sure the branch is checked out in location given on the command
  
    if ($useTemp) {
	my $line = `git remote show origin | grep Fetch`;
	$line =~ /(https:\/\/github.com\/IBMStreams\/(.+)\.git)$/;
	my $url = $1;
	my $repoName = $2;
	system("cd /tmp; git clone $url");
	$pagesLocation = "/tmp/$repoName";
    }

    system("cd $pagesLocation; git checkout gh-pages");
    $? >> 8 == 0 or die "Cannot set branch to gh-pages in $pagesLocation";
    # handle the toolkit; need a better way of specifying which toolkit.
    lookForApp(".","test");
    my $changes = `cd $pagesLocation; git status -s | grep -v "^?" | wc -l`;
    chomp $changes;
    print "$changes files changed\n";
    if ($changes > 0) {
	if ($dryrun) {
	    print "DryRun: $changes files changed\n";
	}
	elsif ($changes >0 && $doCommit) {
	    system("cd $pagesLocation; git commit -a -m \"$message\"");
	    if ($doPush) {
		system("cd $pagesLocation; git push origin gh-pages");
	    }
	}
    }
    if ($useTemp) {
	if ($pagesLocation =~ /tmp/) {
	    system("rm -rf $pagesLocation");
	}
	else {
	    die "Unexpected temporary repository structure; not deleting $pagesLocation";
	}
    }
}

main();
