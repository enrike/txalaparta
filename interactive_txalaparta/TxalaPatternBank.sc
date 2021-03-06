// license GPL
// by www.ixi-audio.net

/* Stores planks data in pattern

*/

TxalaPatternBank{
	var <bank;

	*new {
		^super.new.initTxalaPatternBank();
	}

	initTxalaPatternBank {
		this.reset();
	}

	reset{
		bank = [[],[],[],[]];
	}

	loaddata {arg bankdata;
		bank = bankdata;
	}

	dorandpattern{ arg numhits=1; // just make up a safe random pattern in case there was none to return in bank
		var pat=[], data=(), blueprint="";
		numhits.do({arg index;
			var hitdata = (); // each hit
			hitdata.add(\time   -> (0+(0.05*index)))
			.add(\amp    -> 0.5)
			.add(\player -> 1)
			.add(\plank  -> 0); // default to safe first plank
			pat = pat.add(hitdata);
			blueprint = blueprint+"0";
		});

		data = data.add(\blueprint -> blueprint);
		data = data.add(\pattern -> pat);
		data = data.add(\numtimes -> 1);

		^data;
	}

	getrandpattern{ arg numhits=2;
		var pat="";
		if (numhits > 0, { pat = bank[numhits-1].choose() });
		if (pat.isNil, {pat=""});
		if (pat=="", { // never return nil
			pat = this.dorandpattern(numhits);
		});
		^pat;
	}

	addpattern{ arg apattern;
		var blueprint = "", isthere=false, newpattern=(), size = apattern.size;

		// get pattern blueprint
		apattern.collect({ arg item; item.plank.asString }).do({arg item; blueprint = blueprint++item });

		//apattern = this.normalisetime(apattern);
		//apattern = this.normaliseamp(apattern);

		newpattern = newpattern.add(\blueprint -> blueprint); // plank sequence. 4123, 13, 343, 114 or 22 for instance
		newpattern = newpattern.add(\pattern -> apattern);
		// TO BE DONE. analyse how ofter this blueprint has appeared
		newpattern = newpattern.add(\numtimes -> 1); // to count how many times has appeared this blueprintapattern.size-1

		if (apattern.isNil.not, { // sometimes I get patterns which are nil. WHY?
			bank[apattern.size-1] = bank[apattern.size-1].add(newpattern);
		});
	}

	normaliseamp{arg apattern; // return the pattern data with the amp values normalised
/*		appatern.do({arg hit, index;
			appatern[index].time = hit.time/apattern.last.time // last is length
		})*/
		^apattern;
	}

	normalisetime{arg apattern; // return the pattern data with the time values normalised to 0-1
		if (apattern.size > 1,{
			apattern.do({arg hit, index;
				apattern[index].time = hit.time/apattern.last.time // last is length
			})
		});
		^apattern;
	}
}

