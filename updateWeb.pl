#!/usr/bin/perl

use strict;
use Getopt::Long;


my $debug = 0;
my $dryrun = 0;
my $doPush;
my $doCommit=1; 
my $message = "Update documents";
my $help;

GetOptions('push!'=>\$doPush,
	   'commit!'=>\$doCommit,
	   'dryrun' => \$dryrun,
	   'message|m=s' => \$message,
           'help|h|?'=>\$help);
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
if (scalar @ARGV != 1) {
    print STDERR "Usage: makeDocs.pl <pagesrepo>\n";
    exit 1;
}
my $pagesLocation = $ARGV[0];
die "$pagesLocation not a valid directory" unless (-e $pagesLocation && -d $pagesLocation);


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

sub syncDirectory($$) {
    my ($sourceDir, $destDir) = @_;
    $debug && print "syncDirectory($sourceDir,$destDir)\n";
    if (!(-e $destDir)) {
	system("mkdir -p $destDir");
    }
    my @files = `ls $sourceDir`;
    my $changes = 0;
    for my $file (@files) {
	chomp $file;
	my $basename = $file;
	if ($file =~ /\/([^\/]+)$/) {
	    $basename = $1;
	}
	$debug && print "\t $file basename $basename\n";
	my $fullDest = "$destDir/$basename";
	my $fullSource = "$sourceDir/$basename";
	if (-d $fullSource) {
	    $changes += syncDirectory($fullSource,$fullDest); 
	}
	else {
	    if (-e $fullDest) {
	       # it exists.
		system(("diff", "-q",$fullSource, $fullDest));
		my $rc = $? >> 8;
		if ($rc == 1) {
		    # the files differ
		    my @cmd = ("cp",$fullSource,$fullDest);
		    my $rc = run(@cmd);
		    $changes++;
		}
		else {
		    $debug && print "No action: $fullSource is the same as $fullDest\n";
		}
	    }
	    else {
		my $cmd = quotedollar("cp $fullSource $fullDest;cd $pagesLocation; git add $fullSource");
		my $rc = runString($cmd);
		$changes++;
	    }
	}
    }
    return $changes; 
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
	if (-e "$fullName/info.xml") {
	    $debug && print "directory $fullName appears to have a toolkit\n";
	    # if there is java to build, build it.  
	    if (-e "$fullName/build.xml") {
		system("cd $fullName; ant");
	    }
	    # don't need to make any c++ operators, because they don't create model files.
	    system("spl-make-toolkit -i $fullName; spl-make-doc -i $fullName");
	    $changes += syncDirectory("$fullName/doc","$pagesLocation/$fullName/doc");
	}
	else {
	    $debug && print "$fullName/info.xml does not exist\n";
	    lookForApp("$fullName");
	}
    }
    return $changes;
}

sub main() {
    # Make sure the branch is checked out in location given on the command
    system("cd $pagesLocation; git checkout gh-pages");
    $? == 0 or die "Cannot set branch to gh-pages in $pagesLocation";
    # handle the toolkit; need a better way of specifying which toolkit.
    my $changes = lookForApp(".","test");
    if ($dryrun) {
	print "DryRun: $changes files changed";
    }
    elsif ($changes >0 && $doCommit) {
	system("cd $pagesLocation; git commit -a -m \"Update spl-doc for web page\"");
	if ($doPush) {
	    system("cd $pagesLocation; git push origin gh-pages");
	}
    }
}

main();
