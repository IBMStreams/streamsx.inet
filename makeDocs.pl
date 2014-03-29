#!/usr/bin/perl

use strict;

if (scalar @ARGV != 1) {
    print STDERR "Usage: makeDocs.pl <pagesrepo>\n";
    exit 1;
}

my $debug = 0;
my $dryrun = 0;
my $pagesLocation = $ARGV[0];

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
    for my $f (@files) {
	chomp $f;
	my $fullName = "$dir/$f";
	$debug && print "Trying file $fullName\n";
	next if (defined $exclude && $dir =~ /$exclude/);
	next unless (-d "$fullName");
	if (-e "$fullName/info.xml") {
	    $debug && print "directory $fullName appears to have a toolkit\n";
	    system("spl-make-doc -i $fullName");
	    syncDirectory("$fullName/doc","$pagesLocation/$fullName/doc");
	}
	else {
	    $debug && print "$fullName/info.xml does not exist\n";
	    lookForApp("$fullName");
	}
    }
}

sub main() {
    # Make sure the branch is checked out in location given on the command
    system("cd $pagesLocation; git checkout gh-pages");
    # handle the toolkit; need a better way of specifying which toolkit.
    lookForApp(".","test");
}

main();
