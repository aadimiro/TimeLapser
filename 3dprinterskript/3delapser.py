import sys
import re

def manipulate_gcode(input_file, output_file):
    with open(input_file, 'r') as infile, open(output_file, 'w') as outfile:
        for line in infile:
            outfile.write(line)
            if line.startswith(';layer:'):
                # Insert custom commands after each layer
                outfile.write("G91\n")  # Use relative positioning mode
                outfile.write("G1 E-2\n")  # Retract -2mm at 2600mm/min or 45mm/sec
                outfile.write("G1 Z2\n")  # Move Z up 2mm
                outfile.write("G90\n")  # Use absolute positioning mode
                outfile.write("G1 X65\n")  # Park head
                outfile.write("G1 Y65\n")  # Park head
                outfile.write("G4 S7\n")  # Pause
                outfile.write("G91\n")  # Use relative positioning mode
                outfile.write("G1 Z-2\n")  # Z down
                outfile.write("G90\n")  # Go back to absolute position mode for all axes
def main():
    if len(sys.argv) != 2:
        print("Usage: python 3delapser.py <input_gcode_file>")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = input_file.replace('.gcode', '_mod.gcode')

    manipulate_gcode(input_file, output_file)
    print(f"Modified G-code saved to: {output_file}")

if __name__ == "__main__":
    main()
