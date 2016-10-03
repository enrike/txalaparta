/*
s.boot
t = Txalaparta.new( s, '.' );

~mode=false
// delaytime, mode, txakun?, localamp, localstep, intermakilaswing, numbeats
t.schedulehits(0, true, true, 0.5, 0.01, 0.9, 4);
t.play // plays a task that loops endlessly generating txalaparta rhythms

t.makilaF = {"hit".postln}; // custom function that will be triggered when a hit is triggered
t.scheduleDraw= {"".postln};

*/
Txalaparta{
	var <samples, sndpath, numplanks, netadd, server, autoplayRoutine, interactivePlayRoutine, currenttemposwing, plankresolution;
	var <scoreArray, <startTime, interstepcounter;

	*new {| server, path = ".", numplanks |
		^super.new.initTxalaparta( server, path, numplanks );
	}

	initTxalaparta { |aserver, apath, anumplanks|

		server = aserver;

		sndpath = apath ++ "/sounds/";
		samples = (sndpath++"*").pathMatch;
		("sndpath is" + sndpath).postln;
		//("available samples are" + samples).postln;

		numplanks = anumplanks;
		plankresolution = 5; // how many areas max in each plank for the sample sets

		startTime = 0;
		interstepcounter = 0;

		~buffersATXND = Array.fillND([numplanks, plankresolution], { [] }); // NDimensions sound space 6*5

		//netadd = NetAddr("127.0.0.1", 6666);// which port to use?

		scoreArray = scoreArray.add( // just add an empty event
			().add(\time -> 0)
			.add(\amp -> 0)
			.add(\player -> 1) //1 or 2
			.add(\plank -> 1)
		);

		// globals //
		~verbose = 0;
		~tempo = 60; // tempo. txakun / errena
		~swing = 0.05;
		~gapswing = 0.1;
		~gap = 0.22; // between hits. in txalaparta berria all gaps are more equal
		~amp = 0.5;
		//~classictxakun = true; // in txalaparta zaharra the txakun always 2 hits
		//~pulse = true; // should we keep stedy pulse in the tempo or not?
		~freqs = [1]; //
		~lastemphasis = true; // which one is stronger. actualy just using first or last
		~zerolimit = true; //allow 0 more than once or not?
		~enabled = [true, true]; // switch on/off txakun and errena
		~allowedbeats = [[0, 1, 2, 3, 4], [0, 1, 2, 3, 4]];
		~beatchance = [0.15, 0.25, 0.35, 0.15, 0.1];
		~plankchance = Array.fill(numplanks, {1});
		//~autopilotrange = [5, 10]; // for instance
		//~mode = false; // old style hit position calulation?

		/*		MIDIClient.init;
		MIDIClient.destinations;
		MIDIIn.connectAll;
		~midiout = MIDIOut(0, MIDIClient.destinations.at(0).uid);*/

		~buffersenabled = [Array.fill(numplanks, {false}), Array.fill(numplanks, {false})]; // [enabledtxakun, enablederrena]

		SynthDef(\playBuf, {arg outbus = 0, amp = 1, freq=1, bufnum = 0;
			Out.ar(outbus,
				amp * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * freq, doneAction:2)!2
			)
		}).store;


		// AUTO TXALAPARTA LOOP ////////////////////////
		// endless txalaparta rhythm using to globals
		////////////////////////////////////////////////
		autoplayRoutine = Task({
			var txakun = true; // classical txakun only is limited to two beats
			var localtemposwing=0, zeroflag=false, idealtempo=0;

			inf.do({ arg stepcounter; // txakun > errena cycle
				var numbeats, outstr, beats, outarray=Array.new, scheduletime=0, intermakilaswing, deviation, localamp, localstep=0; // reset each loop

				outarray = outarray.add([1, ("is txakun?" + txakun)]);

				beats =	~allowedbeats[txakun.not.asInt]; // take the ones for this player

				deviation = idealtempo - localtemposwing;  // how far we are from ideal time
				idealtempo = 60/(~tempo*2); // ideal position /2* because it is a binary rhythm. TO DO: check this. why *2?
				if (localtemposwing == 0, {localtemposwing = idealtempo}); // only first time

				// if none is allowed or if only 0 is allowed or no choices to choose any
				// TO DO. more complex. needs to check if the allowed ones have valid choice
				//if ((beats.copyRange(1,beats.size).every(_.isNil) ||
				//	~beatchance.normalizeSum.every(_.isNaN)), {
				if (this.arebeats(beats), {
					"WARNING: no beats allowed or no choice to select any".postln;
				},{
					// beats
					if ( (txakun && ~enabled[0]) || (txakun.not && ~enabled[1]), // enabled?
						{
							if ((~zerolimit && zeroflag), // no two consecutive 0
								{ beats = beats[1..beats.size]});

							{ numbeats == nil }.while({
								numbeats = beats.wchoose(~beatchance.normalizeSum)
							});

							zeroflag = numbeats.asBoolean.not; // true 0, false 1..4 // no two consecutive 0
							localstep = (localtemposwing*~gap*2)/numbeats;
							intermakilaswing = rand(~gapswing/numbeats); //reduces proportionally
							if (~amp > 0, {localamp = ~amp + 0.3.rand-0.15}, {localamp = 0});
							//if (~mode, {scheduletime = localtemposwing});

							this.schedulehits(scheduletime, txakun, localamp,
								localstep, intermakilaswing, numbeats);

							outstr = stepcounter.asString++":"+if(txakun, {"txakun"},{"errena"})+numbeats;
							outarray = outarray.add([1, ["beat", stepcounter, txakun, numbeats]]);

							{ this.postoutput(outarray) }.defer;
					}); //end if beats

				});

				// calculate time of next hit
				currenttemposwing = rrand(~swing.neg, ~swing);
				localtemposwing = idealtempo + currenttemposwing;
				//if (~pulse.not, { localtemposwing = localtemposwing - deviation });

				localtemposwing.wait;

				txakun = txakun.not;
			}) // end inf loop
		}); // end  routine
	}

	/* avoid if none is allowed or chances are none
	*/
	arebeats {arg beats;
		^(beats.copyRange(1, beats.size).every(_.isNil) ||
			~beatchance.normalizeSum.every(_.isNaN))
	}

	/* new single hit detected by txalatempo. add it to score
	*/
	newhit {arg hit;
		["new hit", hit].postln;
		scoreArray = scoreArray.add(hit);
	}

	analisePattern{arg apattern;
		var length = nil, intermakilagap=0, emphasis=0;
		if (apattern.size > 1, {
			length = apattern.last.time - apattern.first.time;
			intermakilagap = length/apattern.size;
			//emphasis = // collect all amps and get the index of the highest one.
		});
		^[length, intermakilagap]; //emphasis];
	}

	autoplay {
		autoplayRoutine.play(SystemClock);
		startTime = Main.elapsedTime;
	}

	autostop {
		autoplayRoutine.stop;
		startTime = 0;
		interstepcounter = 0;
	}

	interactiveplay {
		interactivePlayRoutine.play(SystemClock);
		startTime = Main.elapsedTime;
	}

	interactivestop {
		interactivePlayRoutine.stop;
		startTime = 0;
	}

	loadsampleset{ arg presetfilename;
		var foldername = presetfilename.split($.)[0];// get rid of the file extension
		("load sampleset"+foldername).postln;
		~buffersATXND = Array.fillND([numplanks, plankresolution], { [] }); // clean first
		~buffersATXND.do({arg plank, indexplank;
			plank.do({ arg pos, indexpos;
				10.do({ arg indexamp;// this needs to be dynamically calc from the num of samples for that amp
					var filename = "plank" ++ indexplank.asString++indexpos.asString++indexamp.asString++".wav";
					if ( PathName.new(sndpath ++"/"++foldername++"/"++filename).isFile, {
						var tmpbuffer = Buffer.read(server, sndpath ++"/"++foldername++"/"++filename);
						~buffersATXND[indexplank][indexpos] = ~buffersATXND[indexplank][indexpos].add(tmpbuffer)
					})
				})
			})
		})
	}


	/* receives an array of arrays each of them with two items:
	- verbose level, array of strings to post --> [ [2, ["hello", "world"]], [1, [0,1,2,3,4]] ]
	it posts the mesages that are above ~verbose level
	*/
	postoutput { arg arr;
		arr.do({ arg item;
			if ((~verbose > item[0]), {item[1].postln});
		});
		//arr = [];
	}

	/*	getnumactiveplanks {
	var numactiveplanks=0;
	~buffersATX.do({arg arr, ind; // checks if enabled for any of the players
	if( (~buffersenabled[0][ind]||~buffersenabled[1][ind]),
	{numactiveplanks = numactiveplanks + 1})});
	^numactiveplanks;
	}*/

	/* which buffer positions is this plank?
	*/
	/*	getplankindex { arg plank;
	var pos=0;
	~buffersATX.do({arg buf, index;
	if (buf.bufnum == plank.bufnum, { pos = index });
	});
	^pos;
	}

	checkplankenabled { arg aplank, flags;
	var pos;
	pos = this.getplankindex(aplank);
	^flags[pos].not; // until we choose a plank that is enabled
	}*/


	/* this schedules all the hits for this bar. uses defer
	localstep: distance between hits
	*/
	schedulehits {arg delaytime=0, txakun, localamp, localstep, intermakilaswing, numbeats;

		var flagindex=txakun.not.asInt, outarray=Array.new, emph, drawingSet = Array.fill(numplanks, {[-1, 0, false, 10]});
		var positions, choices, amp, plankamp, plankpos, plank, ranges;

		if (~buffersenabled[1].indexOf(true).isNil.not, {
			if(~lastemphasis,
				{emph = numbeats-1}, // last
				{emph = (numbeats-1).rand} // any but last
			);

			numbeats.do({ arg index; // for each makila one hit
				var hittime, hitfreq, hitamp=localamp, hitswing, actualplank=nil;

				// emphasis
				if (~amp > 0, { // place emphasis
					if (index == emph,
						{ hitamp = localamp + (localamp/5)},
						{ hitamp = localamp + rrand(-0.06, 0.06)}
					);
				});

				// here we could mute randomly one of the hits on >1 phrases to
				// emulate constructions such as ||:|   :|   |:| and so on...
				plank = Array.fill(numplanks, { arg i; i+1-1 }).wchoose(~plankchance.normalizeSum); // 0 to 7

				{
					~buffersenabled[flagindex][plank] == false;
				}.while({var index;
					plank = Array.fill(numplanks, { arg i; i+1-1 }).wchoose(~plankchance.normalizeSum);
				});

				amp = 1;// TO DO: this should not be 1??
				positions = ~buffersATXND[plank].copy.takeThese({ arg item; item.size==0 }); // Number of samples per table and position available. get rid of empty slots. this is not the best way.

				// chances of diferent areas depending on number of areas // ugly way to solve it
/*				if (positions.size==1,{choices = [1]});
				if (positions.size==2,{choices = [0.50, 0.50]});
				if (positions.size==3,{choices = [0.2, 0.65, 0.15]});
				if (positions.size==4,{choices = [0.15, 0.35, 0.35, 0.15]});
				if (positions.size==5,{choices = [0.15, 0.15, 0.3, 0.3, 0.1]}); */

				choices = [ [1], [0.50, 0.50], [0.2, 0.65, 0.15], [0.15, 0.35, 0.35, 0.15], [0.15, 0.15, 0.3, 0.3, 0.1]]; // chances to play in different areas of the plank according to samples available

				// the wchoose needs to be a distribution with more posibilites to happen on center and right
				plankpos = Array.fill(positions.size, {arg n=0; n}).wchoose(choices[positions.size-1]); // a num between 0 and positions.size

				// which sample corresponds to this amp. careful as each pos might have different num of hits inside
				ranges = Array.fill(~buffersATXND[plank][plankpos].size, {arg num=0; (1/~buffersATXND[plank][plankpos].size)*(num+1) });

				plankamp = ranges.detectIndex({arg item; amp<=item});
				if (plankamp.isNil, {plankamp = 1}); // if amp too high it goes nil
				actualplank = ~buffersATXND[plank][plankpos][plankamp];

				hitfreq = ~freqs.choose + rrand(-0.002, 0.002); // just a small freq swing
				hittime = delaytime + (localstep * index);
				if (index > 0, { hittime = hittime + rand(intermakilaswing) });

				~txalascoreAuto.hit(Main.elapsedTime + hittime , hitamp, (flagindex + 1), plank);
				{ // deferred function
					Synth(\playBuf, [\amp, hitamp, \freq, hitfreq, \bufnum, actualplank.bufnum]);

					if (~makilaanims.isNil.not, {
						~makilaanims.makilaF(txakun.not.asInteger, index, 0.2);//slider animation
					});
					//~midiout.noteOn(txakun.not.asInteger, plank.bufnum, hitamp*127);
					//{~midiout.noteOff(txakun.not.asInteger, plank.bufnum, hitamp*127) }.defer(0.2);
					outarray = outarray.add([2, "plank" + actualplank]); // postln which plank this hit
					outarray = outarray.add([2, ["hit", index, hittime, hitamp, hitfreq, hitswing]]);
					this.postoutput(outarray); // finally
					outarray = [];
				}.defer(hittime);

				drawingSet[index] = [currenttemposwing, hittime, txakun, hitamp]; // store for drawing on window.refresh
			}); // END NUMBEATS LOOP

			{
				if (~makilaanims.isNil.not, { ~makilaanims.scheduleDraw(drawingSet) });
			}.defer(delaytime); // schedule drawing when first hit fires

		}, {"WARNING: no sound selected for beat".postln; ~buffersATXND.postln});
	}
}