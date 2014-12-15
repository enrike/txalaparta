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
	var <samples, <>buffers, sndpath, netadd, server, autoplayRoutine, interactivePlayRoutine, currenttemposwing;
	var >makilaF, >scheduleDraw, <scoreArray, <startTime, interstepcounter;

	*new {| server, path = "." |
		^super.new.initTxalaparta( server, path );
	}

	initTxalaparta { |aserver, apath|

		server = aserver;

		sndpath = apath ++ "/sounds/"; //thisProcess.nowExecutingPath.dirname ++ "/sounds/";
		samples = (sndpath++"*").pathMatch;
		("sndpath is" + sndpath).postln;
		("available samples are" + samples).postln;

		startTime = 0;
		interstepcounter = 0;

		buffers = Array.fill(8, {nil});

		netadd = NetAddr("127.0.0.1", 6666);// which port to use?

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
		~pulse = false; // should we keep stedy pulse in the tempo or not?
		~freqs = [1]; //
		~lastemphasis = true; // which one is stronger. actualy just using first or last
		~zerolimit = true; //allow 0 more than once or not?
		~enabled = [true, true]; // switch on/off txakun and errena
		//~allowedbeats = [0, 1, 2, nil, nil]; // 0,1,2,3,4
		~allowedbeats = [[0, 1, 2, 3, 4], [0, 1, 2, 3, 4]];
		~beatchance = [0.15, 0.25, 0.35, 0.15, 0.1];
		~plankchance = (Array.fill(buffers.size, {1}));
		~autopilotrange = [5, 10]; // for instance
		~mode = true; // old style hit position calulation?
		~oscout = false;
		~midiout = false;// not yet

		~buffersenabled = [Array.fill(buffers.size, {false}), Array.fill(buffers.size, {false})]; // [enabledtxakun, enablederrena]

		SynthDef(\playBuf, {arg outbus = 0, amp = 1, freq=1, bufnum = 0;
			Out.ar(outbus,
				amp * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * freq, doneAction:2)!2
			)
		}).store;

		scheduleDraw = {};
		makilaF = {};

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
				idealtempo = 60/(~tempo*2); // ideal position /2* because it is a binary rhythm
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
								if (~mode, {scheduletime = localtemposwing});

								this.schedulehits(scheduletime, ~mode, txakun, localamp,
									localstep, intermakilaswing, numbeats);

								outstr = stepcounter.asString++":"+if(txakun, {"txakun"},{"errena"})+numbeats;
								outarray = outarray.add([1, ["beat", stepcounter, txakun, numbeats]]);

								{ this.postoutput(outarray) }.defer;
						}); //end if beats

				});

				// calculate time of next hit
				currenttemposwing = rrand(~swing.neg, ~swing);
				localtemposwing = idealtempo + currenttemposwing;
				if (~pulse.not, { localtemposwing = localtemposwing - deviation });

				localtemposwing.wait;

				txakun = txakun.not;
			}) // end inf loop
		}); // end  routine

		//////////////////////////////////////////////////////////////////////////////////
		// listens to incoming txalaparta sound stream, tries to find out the tempo and tries to
		// answer in the proper positions
		//////////////////////////////////////////////////////////////////////////////////
/*		interactivePlayRoutine = Task({
			inf.do({ arg step;
				0.1.wait;
			})
		});*/
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
		scoreArray = scoreArray.add(hit);
	}

	analisePattern{arg apattern;
		var length = nil, intermakilagap=0, emphasis=0;

		if (apattern.isNil, {^[0,0,0]});//skip
		if (apattern.size > 1, {
			length = apattern.last.hittime - apattern.first.hittime;
			intermakilagap = length/apattern.size;
			//emphasis = // collect all amps and get the index of the highest one.
		});
		^[length, intermakilagap, emphasis];
	}

	// this gets called when the other interpreter hits the first of its
	// group. we then calculate where our answer should go and schedule it
	finisholdpattern {arg bpm, prevpattern;
		var txakun=false; //fixed
		var localstep, idealtempo=0, localtemposwing=0, localamp, zeroflag=false;
		var numbeats, outstr, beats, outarray=Array.new, scheduletime=0, intermakilaswing, deviation;
		var patterndata;

		prevpattern.postln;
		//patterndata = this.analisePattern(prevpattern);

		scheduletime = (60.0/bpm)/2;
		beats =	~allowedbeats[txakun.not.asInt]; // take the ones for this player

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
					localstep = (~gap*2.0)/numbeats;
					//intermakilaswing = rand(~gapswing/numbeats); //reduces proportionally
					intermakilaswing = rand(patterndata[1]/numbeats);
					if (~amp > 0, {localamp = ~amp + 0.3.rand-0.15}, {localamp = 0});

					this.schedulehits(scheduletime, true, txakun, localamp,
						localstep, intermakilaswing, numbeats);

					// mute while playing myself to avoid listening to myself
					//{txalatempo.tooglelisten(false)}.defer(scheduletime);
					//{txalatempo.tooglelisten(true)}.defer(scheduletime+(localstep*numbeats));

					interstepcounter = interstepcounter + 1;

					outstr = interstepcounter.asString++":"+if(txakun, {"txakun"},{"errena"})+numbeats;
					outarray = outarray.add([1, ["beat", interstepcounter, txakun, numbeats]]);
					{ this.postoutput(outarray) }.defer;
			}); //end if beats
		});
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

	load {arg filename, index;
		["loading"+(sndpath ++ filename) ].postln;
		buffers[index] = Buffer.read(server, sndpath ++ filename);
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

	getnumactiveplanks {
		var numactiveplanks=0;
		buffers.do({arg arr, ind; // checks if enabled for any of the players
			if( (~buffersenabled[0][ind]||~buffersenabled[1][ind]),
				{numactiveplanks = numactiveplanks + 1})});
		^numactiveplanks;
	}

	/* which buffer positions is this plank?
	*/
	getplankindex { arg plank;
		var pos=0;
		buffers.do({arg buf, index;
			if (buf.bufnum == plank.bufnum, { pos = index });
		});
		^pos;
	}

	checkplankenabled { arg aplank, flags;
		var pos;
		pos = this.getplankindex(aplank);
		^flags[pos].not; // until we choose a plank that is enabled
	}


	/* this schedules all the hits for this bar. uses defer
	localstep: distance between hits
	*/
	schedulehits {arg delaytime=0, mode=0, txakun, localamp, localstep, intermakilaswing, numbeats;

		var flagindex=txakun.not.asInt, outarray=Array.new, emph, drawingSet = Array.fill(buffers.size, {[-1, 0, false, 10]});

		// txakun true 1 -> 1 // errena false 0 -> 2
		//if (txakun, {flagindex=1},{flagindex=2});

		if (~buffersenabled[1].indexOf(true).isNil.not,
			{
				if(~mode, { // reverse in old mode. 4 options
					if(~lastemphasis,
						{emph = 0}, // first
						{emph = 1+((numbeats-1).rand)} // any but first
					);
				},{
					if(~lastemphasis,
						{emph = numbeats-1}, // last
						{emph = (numbeats-1).rand} // any but last
					);
				});

				numbeats.do({ arg index; // for each makila one hit
					var hittime, hitfreq, hitamp, hitswing, makilaindex, plank=nil, pos;

					// emphasis
					if (~amp > 0, { // place emphasis
						if (index == emph,
							{ hitamp = localamp + (localamp/5)},
							{ hitamp = localamp + rrand(-0.06, 0.06)}
						);
					});

					if ( ~mode, { // before the bar
						makilaindex = numbeats-index-1;//reverse
					},{ // aftr the bar;
						makilaindex = index;
					});

					// here we could mute randomly one of the hits on >1 phrases to
					// emulate constructions such as ||:|   :|   |:| and so on...
					pos = Array.fill(buffers.size, { arg i; i+1-1 }).wchoose(~plankchance.normalizeSum); // 0 to 7
					{
						~buffersenabled[flagindex][pos] == false;
					}.while({
						pos = Array.fill(buffers.size, { arg i; i+1-1 }).wchoose(~plankchance.normalizeSum);
					});
					plank = buffers[pos];

					hitfreq = ~freqs.choose + rrand(-0.002, 0.002); // just a small freq swing

					if ( ~mode, { // before the bar
						hittime = delaytime - (localstep * index);
						if (index > 0, { hittime = hittime - rand(intermakilaswing) });
					},{ // after the bar;
						hittime = delaytime + (localstep * index);
						if (index > 0, { hittime = hittime + rand(intermakilaswing) });
					});

					{ // deferred function
						Synth(\playBuf, [\amp, hitamp, \freq, hitfreq, \bufnum, plank.bufnum]);

						makilaF.value(txakun.not.asInteger, makilaindex, 0.2);//slider animation

						scoreArray = scoreArray.add( // just add an empty event
							().add(\time -> (Main.elapsedTime - startTime))
							.add(\amp -> hitamp)
							.add(\player -> (flagindex + 1)) //1 or 2
							.add(\plank -> (pos + 1))
						);

						//["schedulehits", scoreArray.size].postln;

						outarray = outarray.add([2, "plank" + plank]); // postln which plank this hit
						outarray = outarray.add([2, ["hit", index, hittime, hitamp, hitfreq, hitswing]]);
						this.postoutput(outarray); // finally
						outarray = [];
					}.defer(hittime);

					drawingSet[index] = [currenttemposwing, hittime, txakun, hitamp]; // store for drawing on window.refresh
				}); // END NUMBEATS LOOP

				{ scheduleDraw.value(drawingSet) }.defer(delaytime); // schedule drawing when first hit fires

		}, {"WARNING: no sound selected for beat".postln; buffers.postln});
	}







}