package lookandsay;

public class LookAndSay {
    public static int middleTwoDigits(int n){
        StringBuilder sb = new StringBuilder();
        sb.append("1");
        for(int i=1; i<n; i++){
            StringBuilder next = new StringBuilder();
            int count = 1;
            char prev = sb.charAt(0);

            for(int j=1;j<sb.length();j++){
                if(sb.charAt(j)==prev)count++;
                else{
                    next.append(count).append(prev);
                    prev = sb.charAt(j);
                    count = 1;
                }
            }
            next.append(count).append(prev);
            sb = next;
        }

        int len =  sb.length();
        String mid = sb.substring(len/2-1, len/2+1);

        return Integer.parseInt(mid);
    }
}
