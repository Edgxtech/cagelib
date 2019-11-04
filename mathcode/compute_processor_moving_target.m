%% Matlab simulation - Arbitrary moving target
%% Extended Kalman Filter multiple sensor source and type fusion
%% Timothy Edge (timmyedge), Dec 12
clear all;
clc;
close all;

serialCount = 0;
uu=1;
theta = 0:pi/20:2*pi; % reused

% Target Geometry - arbitrary shape
X_true = [(10:(20/1000):30); 20:(70/1000):90];
X_true = [X_true [30:(60/1000):90; 90*ones(1,1001)]];
X_true = [X_true [90*ones(1,1001); 90:-(20/1000):70]];
X_true = [X_true [90:-(50/1000):40; 70*ones(1,1001)]];

% Location of assets providing tdoa measurements
x = [80 20 60 50 90 20];
y = [90 10 30 50 10 80];

% Location of assets providing range measurements
x_rssi = [80 30 40 60 20];
y_rssi = [80 30 70 80 100];

% Location of assets providing aoa measurements
x_aoa = [85 45 80];
y_aoa = [85 35 20];

dataLength = length(x);

scrsz = get(0, 'ScreenSize');
fig1 = figure('Position',[1 scrsz(4) scrsz(3) scrsz(4)/1.2],'DoubleBuffer','on')
axis([0 110 0 110]);
axis square;
hold on;

Rho = [0;0;0;0];

X_init = [60;30;0.1;0.1];
Xk = X_init;

Pinit = [1 0 0 0;
         0 1 0 0;
         0 0 1 0;
         0 0 0 1]*2*0.01;

Pk = Pinit;
k = 1;
Thi = [1 0 k 0;
       0 1 0 k;
       0 0 0 0;
       0 0 0 0];

f_meas = [sqrt((x_rssi(2)-X_true(1,1))^2 + (y_rssi(2)-X_true(2,1))^2)];

[xl,yl] = pol2cart(theta,f_meas);
xl=xl+x_rssi(2);yl=yl+y_rssi(2);

%% PLOT OBSERVERS
plot(x_rssi,y_rssi,'k*','LineWidth',3);
plot(x_aoa,y_aoa,'c*','LineWidth',3);

if (dataLength>serialCount)
    serialCount = dataLength;

    plot(x,y,'b*','LineWidth',3);
    hold on
end

%%%% PLOT ALL STARTING MEASUREMENTS

for (j=1:length(x_rssi))
    init_meas(j,:) = [sqrt((x_rssi(j)-X_true(1,1))^2 + (y_rssi(j)-X_true(2,1))^2)];

    theta = 0:pi/20:2*pi;
    [xl(j,:),yl(j,:)] = pol2cart(theta,init_meas(j,:));
    xl(j,:)=xl(j,:)+x_rssi(j);yl(j,:)=yl(j,:)+y_rssi(j);

    rangeMeasures(j) = line('XData',xl(j,:), 'YData',yl(j,:), 'Color','r', ...
'LineWidth',1.5);
end

for (j=1:(length(x)-1))
    init_meas(j,:) = [sqrt((x(1)-X_true(1,1))^2 + (y(1)-X_true(2,1))^2) - sqrt((x(j+1)-X_true(1,1))^2 + (y(j+1)-X_true(2,1))^2)];

    tdoa_units = init_meas(j,:);
    [x_h(j,:),y_h(j,:)] = Hyperbola(x(1),y(1),x(j+1),y(j+1),-2,2,tdoa_units);

    tdoaMeasures(j) = line('XData',x_h(j,:), 'YData',y_h(j,:), 'Color','b', ...
'LineWidth',1.5);
end

for (j=1:length(x_aoa))
    init_meas(j,:) = [atan((y_aoa(j) - X_true(2,1))/(x_aoa(j) - X_true(1,1)))*180/pi];
    init_meas_orig(j,:) = init_meas(j,:);
    if(X_true(1,1)<x_aoa(j))
        init_meas(j,:) = init_meas(j,:)+180;
    end

    xx(j,:) = (x_aoa(j)-100):(x_aoa(j)+100);
    yy(j,:) = tan(pi/180*init_meas_orig(j))*(xx(j,:)-x_aoa(j)) + y_aoa(j);%(y_aoa(i)-tan(f_meas_orig(i))*x_aoa(i));

    aoaMeasures(j) = line('XData',xx(j,:), 'YData',yy(j,:), 'Color','c', ...
'LineWidth',1.5);
end

pause()

bufLVL1 = line('XData',Xk(1), 'YData',Xk(2), 'Color','m', ...
    'Marker','o', 'MarkerSize',15, 'LineWidth',3.5);
bufLVL2 = line('XData',Xk(1), 'YData',Xk(2), 'Color','g', ...
    'Marker','*', 'MarkerSize',5, 'LineWidth',1.5);
bufLVL3 = line('XData',Xk(1), 'YData',Xk(2), 'Color','c', ...
    'Marker','*', 'MarkerSize',5, 'LineWidth',1.5);
bufLVL4 = line('XData',Xk(1), 'YData',Xk(2), 'Color','y', ...
    'Marker','*', 'MarkerSize',5, 'LineWidth',1.5);

plot(Xk(1),Xk(2),'cd','LineWidth',3);

%% error Covariance estimate for time update "prediction"
Qu = [eye(2)*0.01 zeros(2); zeros(2) eye(2)*0.01]; % where Q calculated in DiscMdlFinder.m

estBuffer = ones(4,25);
cepBuffer = zeros(25);

sampleRate = 0;
j = 1;
v=0;
stddev = 100;

sampleRate = 0;

frameCount=0;

%% Simulate target moving
for (k=1:length(X_true))
    innov(3) = 0;

    %% Compute estimate
    while (stddev>(5*innov(3)))

        Xk = (Thi*Xk + Rho*uu);
        Pk = Thi*Pk*Thi' + Qu;
        innov = 0;
        P_innov = 0;

        %% TDOA Innovations
        for(i=1:(length(x)-1))

            f_meas(i,:) = [sqrt((x(1)-X_true(1,k))^2 + (y(1)-X_true(2,k))^2) - sqrt((x(i+1)-X_true(1,k))^2 + (y(i+1)-X_true(2,k))^2)] + 1*(0.5-rand);
            f_est(i,:) = [(sqrt((x(1)-Xk(1))^2 + (y(1)-Xk(2))^2) - sqrt((x(i+1)-Xk(1))^2 + (y(i+1)-Xk(2))^2))];
            r(i,:) = f_meas(i,:) - f_est(i,:);

            R1 = sqrt((x(1)-Xk(1))^2 + (y(1)-Xk(2))^2);
            R2 = sqrt((x(i+1)-Xk(1))^2 + (y(i+1)-Xk(2))^2);

            dfdx(i) = -(x(1)+Xk(1))/R1 - (-x(i+1)+Xk(1))/R2;
            dfdy(i) = -(y(1)+Xk(2))/R1 - (-y(i+1)+Xk(2))/R2;

            H(i,:) = [0 0 0 0];
                H(i,3:4) = [dfdx(i) dfdy(i)];

            tdoa_units = f_est(i);
            [x_h_1,y_h_1] = Hyperbola(x(1),y(1),x(i+1),y(i+1),-2,2,tdoa_units);

            tdoa_units = f_meas(i);
            [x_h(i,:),y_h(i,:)] = Hyperbola(x(1),y(1),x(i+1),y(i+1),-2,2,tdoa_units);

            Rk(i) = [0.1];

            K(i,:) = Pk*H(i,:)'*inv(H(i,:)*Pk*H(i,:)' + Rk(i));%Pk*H(i,:)'*inv(Rk); T% WORKS SINGLE SENSOR%
            %K(i,:) = Pk*H(i,:)'*inv(Rk);
            %r(i,:)

            innov = innov + K(i,:)*(r(i,:) - H(i,:)*Xk);

            P_innov = P_innov - K(i,:)'*H(i,:)*Pk;
        end

        %% Range (RSSI) Innovations
        for(i=1:(length(x_rssi)))

            f_meas(i,:) = [sqrt((x_rssi(i)-X_true(1,k))^2 + (y_rssi(i)-X_true(2,k))^2)] + 1*(0.5-rand);
            f_est(i,:) = [sqrt((x_rssi(i)-Xk(1))^2 + (y_rssi(i)-Xk(2))^2)];
            r(i,:) = f_meas(i,:) - f_est(i,:);

            R1 = sqrt((x_rssi(i)-Xk(1))^2 + (y_rssi(i)-Xk(2))^2);

            dfdx(i) = -(x_rssi(i)-Xk(1))/R1;
            dfdy(i) = -(y_rssi(i)-Xk(2))/R1;

            H(i,:) = [0 0 0 0];
                H(i,3:4) = [dfdx(i) dfdy(i)];

            [xl(i,:),yl(i,:)] = pol2cart(theta,f_meas(i));
            xl(i,:)=xl(i,:)+x_rssi(i);yl(i,:)=yl(i,:)+y_rssi(i);

            Rk(i) = [0.1];

            K(i,:) = Pk*H(i,:)'*inv(H(i,:)*Pk*H(i,:)' + Rk(i));%Pk*H(i,:)'*inv(Rk); T% WORKS SINGLE SENSOR%

            innov = innov + K(i,:)*(r(i,:) - H(i,:)*Xk);

            P_innov = P_innov - K(i,:)'*H(i,:)*Pk;
        end

        %% AOA Innovations
        for(i=1:(length(x_aoa)))

            f_meas(i,:) = [atan((y_aoa(i) - X_true(2,k))/(x_aoa(i) - X_true(1,k)))*180/pi];
            f_meas_orig(i,:) = f_meas(i,:);
            if(X_true(1,k)<x_aoa(i))
                f_meas(i,:) = f_meas(i,:)+180;
            end

            %f_est(i,:) = [asin((y_aoa(i) - Xk(2))/(sqrt((x_aoa(i)-Xk(1))^2 + (y_aoa(i)-Xk(2))^2)))*180/pi];
            f_est(i,:) = [atan((y_aoa(i) - Xk(2))/(x_aoa(i) - Xk(1)))*180/pi];
            if(Xk(1)<x_aoa(i))
                f_est(i,:) = f_est(i,:)+180;
            end

            r(i,:) = f_meas(i,:) - f_est(i,:);

            R1 = (x_aoa(i)-Xk(1))^2 + (y_aoa(i)-Xk(2))^2;

            dfdx(i) = (y_aoa(i)-Xk(2))/R1;  %%Note d/d"x" = "y - y_est"/..... on purpose
            dfdy(i) = -(x_aoa(i)-Xk(1))/R1;

            H(i,:) = [0 0 0 0];
                H(i,3:4) = [dfdx(i) dfdy(i)];

            xx(i,:) = (x_aoa(i)-100):(x_aoa(i)+100);
            yy(i,:) = tan(pi/180*f_meas_orig(i))*(xx(i,:)-x_aoa(i)) + y_aoa(i);%(y_aoa(i)-tan(f_meas_orig(i))*x_aoa(i));

            Rk(i) = [0.1];

            K(i,:) = Pk*H(i,:)'*inv(H(i,:)*Pk*H(i,:)' + Rk(i));%Pk*H(i,:)'*inv(Rk); T% WORKS SINGLE SENSOR%

            innov = innov + K(i,:)*(r(i,:) - H(i,:)*Xk);

            P_innov = P_innov - K(i,:)'*H(i,:)*Pk;
        end

    Xk = Xk + innov';

    stddev = sqrt(v);
    if (j==1)
          stddev = 100; %% prevent filter exiting too early, initial variance will be low while it establishes
    end

Pk = eye(size(Pk))*Pk - P_innov; %% Equiv to (I - KH)*Pk, since  =  I*Pk - Sum(K*H*Pk)
Preal = Pk(1:2,1:2);
j = j+1;
figure(1)
error = abs((Xk(3) + Xk(4))/2);

end
sampleRate = sampleRate+1;
if (sampleRate==25)
    plot(Xk(1),Xk(2),'k+','MarkerSize',3,'LineWidth',2)

    plot(X_true(1,k),X_true(2,k),'g+','MarkerSize',3,'LineWidth',2)
    sampleRate = 0;

    frameCount = frameCount+1;
    A(frameCount)=getframe(fig1);
end

estBuffer = [estBuffer(:,2:25) Xk(:)];

set(bufLVL2, 'XData',estBuffer(1,21:24), 'YData',estBuffer(2,21:24))
set(bufLVL3, 'XData',estBuffer(1,16:20), 'YData',estBuffer(2,16:20))
set(bufLVL4, 'XData',estBuffer(1,1:15), 'YData',estBuffer(2,1:15))

set(bufLVL1, 'XData',estBuffer(1,25), 'YData',estBuffer(2,25))
if ~ishandle(bufLVL1), break; end

for (j=1:length(x_rssi))
    set(rangeMeasures(j),'XData',xl(j,:),'YData',yl(j,:))
end

for (j=1:(length(x)-1))
    set(tdoaMeasures(j),'XData',x_h(j,:),'YData',y_h(j,:))
end

for (j=1:length(x_aoa))
    set(aoaMeasures(j),'XData',xx(j,:),'YData',yy(j,:))
end

save animation.mat A