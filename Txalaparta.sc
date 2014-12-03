/*
s.boot
t = Txalaparta.new( s, '.' );

~mode=false
//txakun, localamp, localstep, intermakilaswing, numbeats
t.schedulehits(true, 0.5, 0.01, 0.9, 4);
t.play


t.samples
t.loadbuffer(''); //TO DO

t.play


todo:
separate all the code related to GUI from the funcionality. use globals and functions to pass values between.
this means to split some of the arrays with values where the structure is [widget, state], [widget, state] they have to be two separated arrays one for the widget and another for the states
*/
Txalaparta{
	var <samples, buffers, sndpath, netadd, server, playRoutine;

	*new {| server, path = "." |
		^super.new.initTxalaparta( server,path );
	}

	initTxalaparta {| aserver, apath |

		server = aserver;

		sndpath = apath ++ "/sounds/"; //thisProcess.nowExecutingPath.dirname ++ "/sounds/";
		samples = (sndpath++"*").pathMatch;
		("sndpath is" + sndpath).postln;
		("available samples are" + samples).postln;

		buffers = Array.fill(8, {[nil,false, false]});// [Buffer, enabledtxakun, enablederrena]
		buffers[0][1] = true; // but enable the first one
		buffers[0][2] = true; // but enable the first one

		buffers.do({ arg buf, index;
			buf[0] = Buffer.read(server, samples[index]);
			["loading", buf[0]].postln;
		});

		netadd = NetAddr("127.0.0.1", 6666);// this needs to be set to a proper port

		~verbose = 2;
		~tempo = 70; // tempo. txakun / errena
		~swing = 0.1;
		~gapswing = 0.1;
		~gap = 0.22; // between hits. in txalaparta berria all gaps are more equal
		~amp = 0.5;
		//~classictxakun = true; // in txalaparta zaharra the txakun always 2 hits
		~pulse = false; // should we keep stedy pulse in the tempo or not?
		~freqs = [230, 231]; //
		~lastemphasis = true; // which one is stronger. actualy just using first or last
		~zerolimit = true; //allow 0 more than once or not?
		~enabled = [true, true]; // switch on/off txakun and errena
		//~allowedbeats = [0, 1, 2, nil, nil]; // 0,1,2,3,4
		~allowedbeats = [[0, 1, 2, 3, 4], [0, 1, 2, 3, 4]];
		~beatchance = [0.15, 0.25, 0.35, 0.15, 0.1];
		~plankchance = (Array.fill(8, {1}));
		~autopilotrange = [5, 10]; // for instance
		~mode = true; // old style hit position calulation?
		~oscout = false;
		~midiout = false;// not yet

		// TXALAPARTA ////////////////////
		playRoutine = Task({
			var txakun; // classical txakun only is limited to two beats
			var intermakilaswing, localstep, idealtempo, localtemposwing, localamp, zeroflag=false;

			txakun = true; // play starts with txakun

			inf.do({ arg stepcounter; // txakun > errena cycle
				var numbeats, outstr, beats, outarray=Array.new; // reset each loop

				outarray = outarray.add([1, ("is txakun?" + txakun)]);

				beats =	~allowedbeats[txakun.not.asInt];

				idealtempo = 60/(~tempo*2); // ideal position tempo /2 because it is a binary rhythm
				localtemposwing = idealtempo + ~swing.rand - (~swing/2); //offset

				if (~pulse, // sets the tempo
					{idealtempo.wait},
					{localtemposwing.wait}
				);

				// if none is allowed or if only 0 is allowed or no choices to choose any
				// TO DO. more complex. needs to check if the allowed ones have valid choice
				if ((beats.copyRange(1,beats.size).every(_.isNil) ||
					~beatchance.normalizeSum.every(_.isNaN)), {
						"WARNING: no beats allowed or no choice to select any".postln;
					},{
						// beats
						if ( (txakun && ~enabled[0]) || (txakun.not && ~enabled[1]), // enabled?
							{
								if ((~zerolimit && zeroflag),{ // no two consecutive 0
									beats=beats[1..beats.size];
								});

								{ numbeats == nil }.while({
									numbeats = beats.wchoose(~beatchance.normalizeSum)
								});

								if (numbeats==0, {zeroflag=true}, {zeroflag=false}); // no two gaps in a row

								// global to all hits in this step
								if (~pulse, // reduce the whole bar (*2) by the ~gap percentage and finally divide by the numbeats
									{localstep = (idealtempo*~gap*2)/numbeats},
									{localstep = (localtemposwing*~gap*2)/numbeats}
								);

								intermakilaswing = rand(~gapswing/numbeats); //reduces proportionally
								if (~amp > 0, {localamp = ~amp + 0.3.rand-0.15}, {localamp = 0}); //local amp swing

								this.schedulehits(txakun, localamp, localstep, intermakilaswing, numbeats);

								outstr = stepcounter.asString++":"+if(txakun, {"txakun"},{"errena"})+numbeats;
								outarray = outarray.add([1, ["beat", stepcounter, txakun, numbeats]]);

								{ this.postoutput(outarray) }.defer;
						}); //end if beats

				});

				txakun = txakun.not;
			}) // end inf loop
		}); // end  routine

	}

	play {
		playRoutine.play(SystemClock);
	}

	stop {
		playRoutine.stop;
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

	bufferstatusfor{ arg index, interpreter, flag;// interpreter can be 1 or 2
		buffers[index][interpreter] = flag;
	}

	/* matches planks with buffers
	*/
	getplankindex { arg plank;
		var pos=0;
		buffers.do({arg buf, index;
			if (buf[0].bufnum==plank[0].bufnum, { pos = index });
		});
		pos + 1;
	}

	getnumactiveplanks {
		var numactiveplanks=0;
		buffers.do({arg arr;
			if( (arr[1]||arr[2]), {numactiveplanks=numactiveplanks+1})
		});
		numactiveplanks;
	}


	/* this schedules all the hits for this bar. uses defer
	localstep: distance between hits
	*/
	schedulehits {arg txakun, localamp, localstep, intermakilaswing, numbeats;

		var flagindex=1, outarray=Array.new, emph;

		// txakun true 1 -> 1 // errena false 0 -> 2
		if (txakun, {flagindex=1},{flagindex=2});

		// avoid when no sound is selected
		if (buffers.deepCollect(1, {|item| item[flagindex]}).find([true]).isNil.not,
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
					var hittime, hitfreq, hitamp, hitswing, makilaindex, plank=[nil, false, false];

					// emphasis
					if (~amp > 0, { // place emphasis
						if (index == emph,
							{ hitamp = localamp + (localamp/5)},
							{ hitamp = localamp + rrand(-0.06, 0.06)}
						);
					});

					// here we could mute randomly one of the hits on >1 phrases to
					// emulate constructions such as ||:|   :|   |:| and so on...

					// choose a plank. (which sample)
					{ plank[flagindex] == false }.while( {
						plank = buffers.wchoose(~plankchance.normalizeSum)
					});

					//freq
					hitfreq = (~freqs.choose) + 0.6.rand; // just a small freq swing

					// time of the hit. first plays now
					hittime = localstep * index;
					if (index > 0, { hittime = hittime + rand(intermakilaswing) });

					{ // deferred function
						Synth(\playBuf, [\amp, hitamp, \freq, 1+rrand(-0.008, 0.008), \bufnum, plank[0].bufnum]);
						//GUI
						//makilaF.value(makilasliders[txakun.not.asInteger].wrapAt(makilaindex), 0.2);//slider animation

						outarray = outarray.add([2, "plank"+plank]); // postln which plank this hit
						outarray = outarray.add([2, ["hit", index, hittime, hitamp, hitfreq, hitswing]]);
						this.postoutput(outarray); // finally
						outarray = [];
					}.defer(hittime);
				}); // END NUMBEATS LOOP

		}, {"WARNING: no sound selected for beat".postln; buffers.postln});
	}







}